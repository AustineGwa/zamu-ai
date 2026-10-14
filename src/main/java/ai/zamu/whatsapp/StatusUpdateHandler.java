package ai.zamu.whatsapp;

import java.util.UUID;

import org.springframework.stereotype.Component;

import ai.zamu.conversations.ConversationService;
import ai.zamu.conversations.MessageStatus;
import ai.zamu.shared.ids.TenantId;
import ai.zamu.shared.jobs.Job;
import ai.zamu.shared.jobs.JobHandler;
import ai.zamu.shared.webhooks.WebhookEvents;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Job {@code apply_message_statuses}: MSG-15 sent, delivered, read and failed from Meta's status webhooks. Statuses
 * only move forward, so retries and out-of-order deliveries are harmless.
 */
@Component
class StatusUpdateHandler implements JobHandler {

    private final WebhookEvents webhookEvents;
    private final ConversationService conversations;
    private final ObjectMapper objectMapper;

    StatusUpdateHandler(WebhookEvents webhookEvents, ConversationService conversations, ObjectMapper objectMapper) {
        this.webhookEvents = webhookEvents;
        this.conversations = conversations;
        this.objectMapper = objectMapper;
    }

    @Override
    public String type() {
        return WhatsAppWebhookService.STATUS_JOB;
    }

    @Override
    public void handle(Job job) {
        TenantId tenantId = job.tenantId();
        UUID eventId = UUID.fromString(objectMapper.readTree(job.payloadJson()).path("eventId").asString(""));
        String raw = webhookEvents.payload(tenantId, eventId).orElse(null);
        if (raw == null) {
            return;
        }
        for (JsonNode status : objectMapper.readTree(raw).path("statuses")) {
            MessageStatus mapped = map(status.path("status").asString(""));
            String waMessageId = status.path("id").asString("");
            if (mapped == null || waMessageId.isEmpty()) {
                continue;
            }
            String errorCode = null;
            JsonNode error = status.path("errors").path(0);
            if (!error.isMissingNode()) {
                errorCode = error.path("code").asString(null);
            }
            conversations.applyDeliveryStatus(tenantId, waMessageId, mapped, errorCode);
        }
        webhookEvents.markProcessed(tenantId, eventId);
    }

    static MessageStatus map(String metaStatus) {
        return switch (metaStatus) {
            case "sent" -> MessageStatus.SENT;
            case "delivered" -> MessageStatus.DELIVERED;
            case "read" -> MessageStatus.READ;
            case "failed" -> MessageStatus.FAILED;
            default -> null;
        };
    }
}
