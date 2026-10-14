package ai.zamu.conversations;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ai.zamu.audit.AuditEvent;
import ai.zamu.audit.AuditService;
import ai.zamu.handovers.HandoverService;
import ai.zamu.shared.ids.TenantId;
import ai.zamu.shared.web.LiveUpdates;
import ai.zamu.users.UserService;

/**
 * Conversations: the engine entry point for channels, the staff inbox, and delivery bookkeeping for the channel
 * senders. Every method takes the tenant from the caller's session or job (NFR-09).
 */
@Service
public class ConversationService {

    private static final Logger log = LoggerFactory.getLogger(ConversationService.class);
    static final int MESSAGES_SHOWN = 200;

    private final ConversationEngine engine;
    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final CustomerRepository customers;
    private final Outbox outbox;
    private final HandoverService handovers;
    private final UserService users;
    private final ObjectProvider<TemplateCatalog> templates;
    private final LiveUpdates live;
    private final AuditService audit;
    private final Clock clock;

    ConversationService(ConversationEngine engine, ConversationRepository conversations, MessageRepository messages,
            CustomerRepository customers, Outbox outbox, HandoverService handovers, UserService users,
            ObjectProvider<TemplateCatalog> templates, LiveUpdates live, AuditService audit, Clock clock) {
        this.engine = engine;
        this.conversations = conversations;
        this.messages = messages;
        this.customers = customers;
        this.outbox = outbox;
        this.handovers = handovers;
        this.users = users;
        this.templates = templates;
        this.live = live;
        this.audit = audit;
        this.clock = clock;
    }

    // ---- Channels ------------------------------------------------------------------------------------

    /** Processes one inbound customer message. Returns false if it was a duplicate delivery (MSG-02). */
    public boolean receive(InboundMessage message) {
        return engine.handle(message) == ConversationEngine.Result.PROCESSED;
    }

    /** MSG-02 pre-check at the webhook, so duplicates aren't even queued. */
    @Transactional(readOnly = true)
    public boolean isKnownMessage(TenantId tenantId, String externalId) {
        return externalId != null && messages.existsByWaMessageId(tenantId, externalId);
    }

    @Transactional(readOnly = true)
    public Optional<OutboundMessage> outboundForSend(TenantId tenantId, UUID messageId) {
        return messages.findById(tenantId, messageId)
                .filter(m -> m.direction() == MessageDirection.OUT)
                .flatMap(m -> conversations.findById(tenantId, m.conversationId()).flatMap(c ->
                        customers.findById(tenantId, c.customerId()).map(cu -> new OutboundMessage(m.id(),
                                c.id(), c.channel(), cu.phone(), m.type(), m.body(),
                                outbox.parseTemplate(m.templateJson()), m.status(), c.windowOpen(clock.instant())))));
    }

    @Transactional
    public void markSent(TenantId tenantId, UUID messageId, String externalId) {
        messages.markSent(tenantId, messageId, externalId, clock.instant());
        publishMessageChange(tenantId, messageId);
    }

    @Transactional
    public void markFailed(TenantId tenantId, UUID messageId, String errorCode) {
        messages.updateStatus(tenantId, messageId, MessageStatus.FAILED, errorCode, clock.instant());
        log.warn("Outbound message {} failed: {}", messageId, errorCode);
        publishMessageChange(tenantId, messageId);
    }

    /** MSG-15: delivery status from the channel's status webhooks; only moves forward. Unknown IDs are ignored. */
    @Transactional
    public void applyDeliveryStatus(TenantId tenantId, String externalId, MessageStatus status, String errorCode) {
        messages.findByWaMessageId(tenantId, externalId).ifPresent(m -> {
            if (m.status().canMoveTo(status)) {
                messages.updateStatus(tenantId, m.id(), status, errorCode, clock.instant());
                if (status == MessageStatus.FAILED) {
                    log.warn("WhatsApp reported message {} failed: {}", m.id(), errorCode);
                }
                live.publish(tenantId, "conversation-" + m.conversationId(), m.id().toString());
            }
        });
    }

    // ---- Inbox ---------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<InboxRow> inbox(TenantId tenantId, InboxFilter filter, String search) {
        return conversations.inbox(tenantId, filter, search, 100);
    }

    @Transactional(readOnly = true)
    public ConversationDetail detail(TenantId tenantId, UUID conversationId) {
        Conversation c = conversations.findById(tenantId, conversationId).orElseThrow(ConversationNotFoundException::new);
        Customer customer = customers.findById(tenantId, c.customerId()).orElseThrow(ConversationNotFoundException::new);
        Map<UUID, String> staffNames = users.namesById(tenantId);
        List<MessageView> views = messages.findRecent(tenantId, c.id(), MESSAGES_SHOWN).stream()
                .map(m -> view(m, staffNames))
                .toList();
        return new ConversationDetail(c.id(), customer.id(), customer.name(), customer.phone(), customer.language(),
                customer.blocked(), customer.optedOutAt() != null, c.channel(), c.status(), c.assignedUserId(),
                c.windowOpen(clock.instant()), c.windowExpiresAt(), views);
    }

    /** Clears the unread count when a staff member opens the conversation. */
    @Transactional
    public void markRead(TenantId tenantId, UUID conversationId) {
        conversations.resetUnread(tenantId, conversationId);
    }

    /**
     * HO-07: staff reply in free text, only inside the 24-hour window. Replying to a chat Zamu is handling takes it
     * over (ASSUMPTION: the spec doesn't say; this stops the bot and staff talking over each other).
     */
    @Transactional
    public void staffReply(TenantId tenantId, UUID conversationId, UUID userId, String text) {
        Conversation c = conversations.findById(tenantId, conversationId).orElseThrow(ConversationNotFoundException::new);
        if (!c.windowOpen(clock.instant())) {
            throw new WindowClosedException();
        }
        takeOverIfNeeded(tenantId, c, userId);
        outbox.text(tenantId, c, MessageSender.STAFF, userId, text.trim());
        handovers.recordStaffReply(tenantId, conversationId, clock.instant());
    }

    /** HO-07: an approved template, allowed at any time. */
    @Transactional
    public void staffTemplate(TenantId tenantId, UUID conversationId, UUID userId, String name, String language,
            List<String> params) {
        Conversation c = conversations.findById(tenantId, conversationId).orElseThrow(ConversationNotFoundException::new);
        TemplateCatalog catalog = templates.getIfAvailable();
        TemplateCatalog.TemplateOption option = catalog == null ? null
                : catalog.find(tenantId, name, language).orElse(null);
        List<String> values = params == null ? List.of() : params.stream().map(String::trim).toList();
        if (option == null || values.size() != option.parameterCount() || values.stream().anyMatch(String::isEmpty)) {
            throw new TemplateNotAvailableException();
        }
        takeOverIfNeeded(tenantId, c, userId);
        outbox.template(tenantId, c, userId, new TemplateRef(name, language, values), option.render(values));
        handovers.recordStaffReply(tenantId, conversationId, clock.instant());
    }

    @Transactional(readOnly = true)
    public List<TemplateCatalog.TemplateOption> approvedTemplates(TenantId tenantId) {
        TemplateCatalog catalog = templates.getIfAvailable();
        return catalog == null ? List.of() : catalog.approved(tenantId);
    }

    /** MSG-16: staff block or unblock a customer; Zamu stops replying to blocked customers. */
    @Transactional
    public void setCustomerBlocked(TenantId tenantId, UUID customerId, boolean blocked) {
        if (customers.setBlocked(tenantId, customerId, blocked, clock.instant()) == 0) {
            throw new ConversationNotFoundException();
        }
        audit.record(AuditEvent.of(tenantId, blocked ? "customer.blocked" : "customer.unblocked", "customer", customerId));
    }

    // ---- Handover events (published by the handovers package) ------------------------------------

    public void applyStatus(TenantId tenantId, UUID conversationId, ConversationStatus status, UUID assignedUserId) {
        if (conversations.updateStatus(tenantId, conversationId, status, assignedUserId, clock.instant()) == 0) {
            throw new ConversationNotFoundException();
        }
    }

    private void takeOverIfNeeded(TenantId tenantId, Conversation c, UUID userId) {
        if (c.status() != ConversationStatus.HANDED_OVER || c.assignedUserId() == null) {
            handovers.staffTakeover(tenantId, c.id(), userId);
        }
    }

    private void publishMessageChange(TenantId tenantId, UUID messageId) {
        messages.findById(tenantId, messageId).ifPresent(m ->
                live.publish(tenantId, "conversation-" + m.conversationId(), messageId.toString()));
    }

    private static MessageView view(Message m, Map<UUID, String> staffNames) {
        String label = switch (m.sender()) {
            case CUSTOMER -> "Customer";
            case BOT -> "Zamu";
            case SYSTEM -> "System";
            case STAFF -> staffNames.getOrDefault(m.senderUserId(), "Staff");
        };
        String mediaKind = m.mediaRef() == null ? null : m.mediaRef().split(":", 2)[0];
        return new MessageView(m.id(), m.direction(), m.sender(), label, m.type(), m.body(), mediaKind, m.status(),
                m.errorCode(), m.createdAt());
    }

}
