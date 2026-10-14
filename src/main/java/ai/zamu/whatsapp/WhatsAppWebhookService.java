package ai.zamu.whatsapp;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ai.zamu.conversations.ConversationService;
import ai.zamu.shared.ids.TenantId;
import ai.zamu.shared.jobs.JobQueue;
import ai.zamu.shared.jobs.NewJob;
import ai.zamu.shared.webhooks.WebhookEvents;
import ai.zamu.shared.webhooks.WebhookSource;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Handles a verified webhook POST quickly and hands the work to jobs (spec: Messaging, "acknowledge within a few
 * seconds"):
 * <ul>
 *   <li>MSG-03 routes each change to its tenant by the receiving phone number ID;</li>
 *   <li>stores the change as received (webhook_events), then enqueues one job per new customer message, in a
 *       queue per customer so messages are processed one at a time, in order (MSG-05);</li>
 *   <li>MSG-02 skips message IDs already stored, so Meta's retries are not queued twice;</li>
 *   <li>delivery statuses (MSG-15) go to their own job; template review results are applied straight away.</li>
 * </ul>
 * The stored payload and the jobs commit together.
 */
@Service
class WhatsAppWebhookService {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppWebhookService.class);

    static final String INBOUND_JOB = "process_inbound_message";
    static final String STATUS_JOB = "apply_message_statuses";

    private final WhatsAppAccountService accounts;
    private final TemplateService templates;
    private final ConversationService conversations;
    private final WebhookEvents webhookEvents;
    private final JobQueue jobs;
    private final ObjectMapper objectMapper;

    WhatsAppWebhookService(WhatsAppAccountService accounts, TemplateService templates,
            ConversationService conversations, WebhookEvents webhookEvents, JobQueue jobs, ObjectMapper objectMapper) {
        this.accounts = accounts;
        this.templates = templates;
        this.conversations = conversations;
        this.webhookEvents = webhookEvents;
        this.jobs = jobs;
        this.objectMapper = objectMapper;
    }

    /** @param body the verified raw body */
    @Transactional
    public void receive(byte[] body) {
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (RuntimeException e) {
            log.warn("WhatsApp webhook body is not JSON; ignored");
            return;
        }
        for (JsonNode entry : root.path("entry")) {
            for (JsonNode change : entry.path("changes")) {
                String field = change.path("field").asString("");
                JsonNode value = change.path("value");
                switch (field) {
                    case "messages" -> messages(value);
                    case "message_template_status_update" -> templateStatus(entry.path("id").asString(""), value);
                    default -> log.debug("WhatsApp webhook field {} ignored", field);
                }
            }
        }
    }

    private void messages(JsonNode value) {
        String phoneNumberId = value.path("metadata").path("phone_number_id").asString("");
        Optional<TenantId> routed = accounts.tenantFor(phoneNumberId);
        String raw = objectMapper.writeValueAsString(value);
        if (routed.isEmpty()) {
            webhookEvents.store(WebhookSource.META, null, raw);
            log.warn("WhatsApp webhook for an unknown phone number ID; stored unrouted");
            return;
        }
        TenantId tenantId = routed.get();
        accounts.markWebhookVerified(tenantId);
        UUID eventId = webhookEvents.store(WebhookSource.META, tenantId, raw);

        int queued = 0;
        for (JsonNode message : value.path("messages")) {
            String waMessageId = message.path("id").asString("");
            String from = message.path("from").asString("");
            if (waMessageId.isEmpty() || from.isEmpty()) {
                continue;
            }
            if (conversations.isKnownMessage(tenantId, waMessageId)) {
                continue; // MSG-02
            }
            jobs.enqueue(NewJob.of(INBOUND_JOB, tenantId, Map.of("eventId", eventId.toString(), "waMessageId", waMessageId))
                    .lockKey(customerLockKey(tenantId, from)));
            queued++;
        }
        if (value.path("statuses").size() > 0) {
            jobs.enqueue(NewJob.of(STATUS_JOB, tenantId, Map.of("eventId", eventId.toString())));
            queued++;
        }
        if (queued == 0) {
            webhookEvents.markProcessed(tenantId, eventId);
        }
    }

    private void templateStatus(String wabaId, JsonNode value) {
        Optional<TenantId> routed = accounts.tenantForWaba(wabaId);
        String raw = objectMapper.writeValueAsString(value);
        UUID eventId = webhookEvents.store(WebhookSource.META, routed.orElse(null), raw);
        if (routed.isEmpty()) {
            log.warn("Template status webhook for an unknown WhatsApp Business Account; stored unrouted");
            return;
        }
        templates.applyMetaStatus(routed.get(),
                value.path("message_template_name").asString(""),
                value.path("message_template_language").asString(""),
                value.path("event").asString(""),
                blankToNull(value.path("reason").asString("")));
        webhookEvents.markProcessed(routed.get(), eventId);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() || "NONE".equals(s) ? null : s;
    }

    /** One queue per customer: "cust:" + tenant + ":" + number, at most 57 characters. */
    static String customerLockKey(TenantId tenantId, String from) {
        String digits = from.replaceAll("\\D", "");
        if (digits.length() > 15) {
            digits = digits.substring(0, 15);
        }
        return "cust:" + tenantId.value() + ":" + digits;
    }
}
