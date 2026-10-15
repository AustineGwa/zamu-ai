package ai.zamu.whatsapp;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import ai.zamu.conversations.Channel;
import ai.zamu.conversations.ConversationService;
import ai.zamu.conversations.InboundMessage;
import ai.zamu.conversations.MessageType;
import ai.zamu.shared.ids.TenantId;
import ai.zamu.shared.jobs.Job;
import ai.zamu.shared.jobs.JobHandler;
import ai.zamu.shared.webhooks.WebhookEvents;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Job {@code process_inbound_message}: turns one stored WhatsApp message into an {@link InboundMessage} and runs the
 * conversation engine. MSG-06: marks it read with the typing indicator first (best effort; never blocks the reply).
 * Idempotent: the engine drops a message ID it already stored (MSG-02).
 */
@Component
class InboundMessageHandler implements JobHandler {

    private static final Logger log = LoggerFactory.getLogger(InboundMessageHandler.class);

    private final WebhookEvents webhookEvents;
    private final WhatsAppAccountService accounts;
    private final WhatsAppApi api;
    private final ConversationService conversations;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    InboundMessageHandler(WebhookEvents webhookEvents, WhatsAppAccountService accounts, WhatsAppApi api,
            ConversationService conversations, ObjectMapper objectMapper, Clock clock) {
        this.webhookEvents = webhookEvents;
        this.accounts = accounts;
        this.api = api;
        this.conversations = conversations;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public String type() {
        return WhatsAppWebhookService.INBOUND_JOB;
    }

    @Override
    public void handle(Job job) {
        TenantId tenantId = job.tenantId();
        JsonNode payload = objectMapper.readTree(job.payloadJson());
        UUID eventId = UUID.fromString(payload.path("eventId").asString(""));
        String waMessageId = payload.path("waMessageId").asString("");

        String raw = webhookEvents.payload(tenantId, eventId).orElse(null);
        if (raw == null) {
            log.warn("Webhook event {} not found for job {}; nothing to do", eventId, job.id());
            return;
        }
        JsonNode value = objectMapper.readTree(raw);
        JsonNode message = null;
        for (JsonNode m : value.path("messages")) {
            if (waMessageId.equals(m.path("id").asString(""))) {
                message = m;
                break;
            }
        }
        if (message == null) {
            return;
        }
        if (conversations.isKnownMessage(tenantId, waMessageId)) {
            return; // MSG-02
        }

        if (job.attempt() <= 1) {
            accounts.credentials(tenantId).ifPresent(c -> {
                try {
                    api.markReadWithTyping(c, waMessageId);
                } catch (WhatsAppApiException e) {
                    log.info("Mark-as-read failed ({}); continuing", e.code());
                }
            });
        }

        InboundMessage inbound = toInbound(tenantId, value, message, clock.instant());
        conversations.receive(inbound);
        webhookEvents.markProcessed(tenantId, eventId);
    }

    /** Maps Meta's message types: text, interactive and quick-reply buttons as text; everything else is media (MSG-13). */
    static InboundMessage toInbound(TenantId tenantId, JsonNode value, JsonNode message, Instant fallbackTime) {
        String from = message.path("from").asString("");
        String type = message.path("type").asString("");
        long epoch = message.path("timestamp").asLong(0);
        Instant receivedAt = epoch > 0 ? Instant.ofEpochSecond(epoch) : fallbackTime;

        String profileName = null;
        for (JsonNode contact : value.path("contacts")) {
            if (from.equals(contact.path("wa_id").asString(""))) {
                profileName = blankToNull(contact.path("profile").path("name").asString(""));
            }
        }

        MessageType messageType;
        String text = null;
        String mediaRef = null;
        String replyId = null;
        switch (type) {
            case "text" -> {
                messageType = MessageType.TEXT;
                text = message.path("text").path("body").asString("");
            }
            case "interactive" -> {
                messageType = MessageType.INTERACTIVE;
                JsonNode interactive = message.path("interactive");
                JsonNode reply = interactive.has("button_reply") ? interactive.path("button_reply")
                        : interactive.path("list_reply");
                text = reply.path("title").asString("");
                replyId = reply.path("id").asString("");
            }
            case "button" -> { // a quick-reply button on a template, e.g. the reminder (BK-11)
                messageType = MessageType.INTERACTIVE;
                text = message.path("button").path("text").asString("");
                replyId = message.path("button").path("payload").asString("");
            }
            default -> {
                messageType = MessageType.MEDIA;
                String mediaId = message.path(type).path("id").asString("");
                mediaRef = (type.isEmpty() ? "unknown" : type) + (mediaId.isEmpty() ? "" : ":" + mediaId);
            }
        }
        return new InboundMessage(Channel.WHATSAPP, tenantId, from, profileName, message.path("id").asString(""),
                messageType, text, mediaRef, receivedAt, replyId);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
