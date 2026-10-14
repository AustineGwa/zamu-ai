package ai.zamu.conversations;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import ai.zamu.handovers.HandoverOutcome;
import ai.zamu.handovers.HandoverReason;
import ai.zamu.handovers.HandoverService;
import ai.zamu.shared.ids.TenantId;
import ai.zamu.shared.ids.UuidV7;
import ai.zamu.shared.web.LiveUpdates;
import ai.zamu.tenants.TenantService;
import ai.zamu.tenants.TenantView;

/**
 * The conversation engine (spec: Messaging and the conversation engine): a deterministic state machine that decides
 * what each inbound message needs. It is channel-neutral: WhatsApp calls it today, voice will later.
 *
 * <p>Order of rules for each message:
 * <ol>
 *   <li>MSG-02 duplicate delivery: stop.</li>
 *   <li>MSG-04 find or create the customer and conversation; MSG-17 count a billable conversation.</li>
 *   <li>Store the message, open the 24-hour window (MSG-14), notify the inbox.</li>
 *   <li>Blocked customer, or a chat already with staff (MSG-08): store only.</li>
 *   <li>Staff-only or paused business (MSG-09): holding reply once, chat to the inbox.</li>
 *   <li>MSG-10 greeting for a new customer's first conversation.</li>
 *   <li>MSG-13 non-text; MSG-19 "stop"; MSG-18 "person"; greeting only; otherwise the answer step.</li>
 * </ol>
 * Runs in one job per message, one at a time per customer (MSG-05, job lock key).
 */
@Component
class ConversationEngine {

    private static final Logger log = LoggerFactory.getLogger(ConversationEngine.class);

    enum Result { PROCESSED, DUPLICATE }

    private final CustomerRepository customers;
    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final Outbox outbox;
    private final TenantService tenants;
    private final HandoverService handovers;
    private final AnswerProvider answers;
    private final LiveUpdates live;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    ConversationEngine(CustomerRepository customers, ConversationRepository conversations, MessageRepository messages,
            Outbox outbox, TenantService tenants, HandoverService handovers, AnswerProvider answers, LiveUpdates live,
            ApplicationEventPublisher events, Clock clock) {
        this.customers = customers;
        this.conversations = conversations;
        this.messages = messages;
        this.outbox = outbox;
        this.tenants = tenants;
        this.handovers = handovers;
        this.answers = answers;
        this.live = live;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public Result handle(InboundMessage in) {
        TenantId tenantId = in.tenantId();
        if (in.externalId() != null && messages.existsByWaMessageId(tenantId, in.externalId())) {
            return Result.DUPLICATE; // MSG-02
        }
        Instant now = clock.instant();
        TenantView tenant = tenants.get(tenantId);

        // MSG-04: customer on first contact
        boolean newCustomer = false;
        Customer customer = customers.findByPhone(tenantId, in.phone()).orElse(null);
        if (customer == null) {
            UUID id = UuidV7.next(clock);
            customers.insert(tenantId, id, in.phone(), blankToNull(in.profileName()), now);
            customer = customers.findById(tenantId, id).orElseThrow();
            newCustomer = true;
        } else if (in.profileName() != null && customer.name() == null) {
            customers.updateNameIfMissing(tenantId, customer.id(), in.profileName().trim(), now);
        }

        // MSG-04: conversation, new when none is open or the last customer message is over 24 hours old
        Conversation previous = conversations.findLatest(tenantId, customer.id(), in.channel()).orElse(null);
        boolean newConversation = previous == null || !previous.continuesAt(in.receivedAt());
        Conversation conversation;
        if (newConversation) {
            if (previous != null && previous.status() != ConversationStatus.CLOSED) {
                conversations.updateStatus(tenantId, previous.id(), ConversationStatus.CLOSED, null, now);
                handovers.resolveSuperseded(tenantId, previous.id());
            }
            UUID id = UuidV7.next(clock);
            conversations.insert(tenantId, id, customer.id(), in.channel(), now);
            conversation = conversations.findById(tenantId, id).orElseThrow();
        } else {
            conversation = previous;
        }

        // MSG-17: billable once per customer per 24 hours from their first message
        if (conversation.billablePeriodExpired(now)) {
            conversations.startBillablePeriod(tenantId, conversation.id(), now);
            events.publishEvent(new ConversationEvents.BillableConversationStarted(tenantId, now));
        }

        // Store the message; MSG-14 window runs 24 hours from this message
        messages.insert(tenantId, new Message(UuidV7.next(clock), tenantId.value(), conversation.id(),
                MessageDirection.IN, MessageSender.CUSTOMER, null, in.externalId(), in.type(), in.text(),
                in.mediaRef(), null, MessageStatus.RECEIVED, null, now, now));
        conversations.recordCustomerMessage(tenantId, conversation.id(), now);
        conversation = conversations.findById(tenantId, conversation.id()).orElseThrow();
        events.publishEvent(new ConversationEvents.MessageRecorded(tenantId, MessageDirection.IN, now));
        live.publish(tenantId, "conversation-" + conversation.id(), "");
        live.publish(tenantId, "inbox", conversation.id().toString());

        if (customer.blocked() || customer.deletedAt() != null) {
            return Result.PROCESSED;
        }
        if (conversation.status() == ConversationStatus.HANDED_OVER) {
            return Result.PROCESSED; // MSG-08: staff have it; never reply as the bot
        }

        String language = language(tenantId, customer, in.text(), tenant);

        // MSG-09: staff-only or paused
        if (!tenant.botActive()) {
            if (!conversation.holdingReplySent()) {
                String holding = tenant.holdingReply() != null ? tenant.holdingReply() : StandardMessages.holding(language);
                outbox.text(tenantId, conversation, MessageSender.BOT, null, holding);
                conversations.markHoldingReplySent(tenantId, conversation.id());
            }
            handOver(tenantId, conversation, HandoverReason.STAFF_ONLY, language, false);
            return Result.PROCESSED;
        }

        // MSG-10: greeting at the start of a new customer's first conversation (Appendix B: "first message to a new customer")
        boolean greeted = false;
        if (newCustomer) {
            outbox.text(tenantId, conversation, MessageSender.BOT, null,
                    StandardMessages.greeting(language, tenant.displayName()));
            greeted = true;
        }

        if (in.type() == MessageType.MEDIA) {
            outbox.text(tenantId, conversation, MessageSender.BOT, null, StandardMessages.nonText(language)); // MSG-13
            return Result.PROCESSED;
        }
        String text = in.text() == null ? "" : in.text();
        if (Keywords.isStop(text)) {
            customers.optOut(tenantId, customer.id(), now); // MSG-19
            outbox.text(tenantId, conversation, MessageSender.BOT, null, StandardMessages.optedOut(language));
            return Result.PROCESSED;
        }
        if (Keywords.asksForPerson(text)) {
            handOver(tenantId, conversation, HandoverReason.REQUESTED, language, true); // MSG-18
            return Result.PROCESSED;
        }
        if (greeted && Keywords.isGreetingOnly(text)) {
            return Result.PROCESSED; // the greeting is the reply
        }

        AnswerProvider.Answer answer = answers.answer(new AnswerProvider.Question(tenantId, conversation.id(),
                customer.id(), text, language));
        if (answer.text() != null && !answer.text().isBlank()) {
            outbox.text(tenantId, conversation, MessageSender.BOT, null, answer.text());
        }
        if (answer.handover() != null) {
            // The answer already says it is connecting the customer; add only the after-hours notice if needed.
            handOver(tenantId, conversation, answer.handover(), language, false);
        }
        return Result.PROCESSED;
    }

    /**
     * HO-01, HO-02: opens the handover, moves the conversation to staff and tells the customer: the handover message
     * (when {@code announce}) while staff are on duty, otherwise the after-hours message with their return time.
     */
    private void handOver(TenantId tenantId, Conversation conversation, HandoverReason reason, String language,
            boolean announce) {
        HandoverOutcome outcome = handovers.request(tenantId, conversation.id(), reason);
        conversations.updateStatus(tenantId, conversation.id(), ConversationStatus.HANDED_OVER, null, clock.instant());
        if (reason == HandoverReason.STAFF_ONLY) {
            return; // the holding reply already told the customer
        }
        if (outcome.staffOnDuty()) {
            if (announce) {
                outbox.text(tenantId, conversation, MessageSender.BOT, null, StandardMessages.handover(language));
            }
        } else {
            ZoneId zone = outcome.nextStaffTime() != null ? outcome.nextStaffTime().getZone() : ZoneId.of("Africa/Nairobi");
            LocalDate today = clock.instant().atZone(zone).toLocalDate();
            outbox.text(tenantId, conversation, MessageSender.BOT, null,
                    StandardMessages.afterHours(language, outcome.nextStaffTime(), today));
        }
        log.info("Conversation {} handed over ({})", conversation.id(), reason);
    }

    /** The customer's language: remembered, else guessed from this message, else the business default (AI-02). */
    private String language(TenantId tenantId, Customer customer, String text, TenantView tenant) {
        String guess = LanguageGuess.guess(text);
        if (guess != null && !guess.equals(customer.language())) {
            customers.updateLanguage(tenantId, customer.id(), guess, clock.instant());
            return guess;
        }
        if (customer.language() != null) {
            return customer.language();
        }
        return tenant.defaultLanguage().name().toLowerCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
