package ai.zamu.privacy;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;

import ai.zamu.ai.EvaluationService;
import ai.zamu.audit.AuditEvent;
import ai.zamu.audit.AuditService;
import ai.zamu.bookings.BookingService;
import ai.zamu.conversations.ConversationService;
import ai.zamu.conversations.CustomerSummary;
import ai.zamu.handovers.HandoverService;
import ai.zamu.knowledge.KnowledgeService;
import ai.zamu.shared.ids.TenantId;
import ai.zamu.shared.jobs.JobQueue;
import ai.zamu.shared.web.NotFoundException;
import ai.zamu.tenants.ChangeRequestService;
import ai.zamu.tenants.TenantService;
import ai.zamu.tenants.TenantStatus;
import ai.zamu.tenants.TenantView;
import ai.zamu.whatsapp.TemplateService;
import ai.zamu.whatsapp.WhatsAppAccountService;

/**
 * Privacy (NFR-11 retention, NFR-12 per-customer export and deletion, NFR-14 audit). Each feature package deletes
 * its own tables; this service decides what and when.
 */
@Service
public class PrivacyService {

    private static final Logger log = LoggerFactory.getLogger(PrivacyService.class);
    /** Finished and dead jobs are kept this long for troubleshooting. */
    static final Duration JOB_RETENTION = Duration.ofDays(30);

    private final RetentionRepository retention;
    private final ConversationService conversations;
    private final HandoverService handovers;
    private final BookingService bookings;
    private final KnowledgeService knowledge;
    private final EvaluationService evaluation;
    private final WhatsAppAccountService whatsapp;
    private final TemplateService templates;
    private final ChangeRequestService changeRequests;
    private final TenantService tenants;
    private final JobQueue jobs;
    private final AuditService audit;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final boolean enabled;

    PrivacyService(RetentionRepository retention, ConversationService conversations, HandoverService handovers,
            BookingService bookings, KnowledgeService knowledge, EvaluationService evaluation,
            WhatsAppAccountService whatsapp, TemplateService templates, ChangeRequestService changeRequests,
            TenantService tenants, JobQueue jobs, AuditService audit, ObjectMapper objectMapper, Clock clock,
            @Value("${zamu.jobs.enabled:true}") boolean enabled) {
        this.retention = retention;
        this.conversations = conversations;
        this.handovers = handovers;
        this.bookings = bookings;
        this.knowledge = knowledge;
        this.evaluation = evaluation;
        this.whatsapp = whatsapp;
        this.templates = templates;
        this.changeRequests = changeRequests;
        this.tenants = tenants;
        this.jobs = jobs;
        this.audit = audit;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.enabled = enabled;
    }

    // --- NFR-12 ---

    public Optional<CustomerSummary> findCustomer(TenantId tenantId, String phone) {
        return conversations.findCustomer(tenantId, phone);
    }

    /** Everything Zamu holds about one customer, as JSON. The export itself is audited (NFR-14). */
    @Transactional(readOnly = true)
    public byte[] exportCustomer(TenantId tenantId, UUID customerId) {
        Map<String, Object> out = new LinkedHashMap<>();
        TenantView tenant = tenants.get(tenantId);
        out.put("business", tenant.displayName());
        out.put("exported_at", clock.instant().toString());
        out.put("customer", conversations.exportCustomer(tenantId, customerId));
        List<UUID> conversationIds = conversations.conversationIdsForCustomer(tenantId, customerId);
        out.put("handovers", handovers.exportForConversations(tenantId, conversationIds));
        out.put("bookings", bookings.exportForCustomer(tenantId, customerId));
        out.put("payments", List.of()); // Milestone 6
        audit.record(AuditEvent.of(tenantId, "customer.exported", "customer", customerId));
        return objectMapper.writer().with(SerializationFeature.INDENT_OUTPUT).writeValueAsString(out)
                .getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Deletes a customer and everything about them except payment records (scenario 16). The audit entry keeps
     * only the customer's ID, no personal data.
     */
    @Transactional
    public void deleteCustomer(TenantId tenantId, UUID customerId) {
        if (conversations.exportCustomer(tenantId, customerId).isEmpty()) {
            throw new NotFoundException("Customer not found");
        }
        bookings.deleteForCustomer(tenantId, customerId);
        handovers.deleteForConversations(tenantId, conversations.conversationIdsForCustomer(tenantId, customerId));
        conversations.deleteCustomer(tenantId, customerId);
        audit.record(AuditEvent.of(tenantId, "customer.deleted", "customer", customerId));
    }

    // --- NFR-11 ---

    @Transactional(readOnly = true)
    public RetentionSettings settings(TenantId tenantId) {
        return retention.find(tenantId).orElse(RetentionSettings.DEFAULTS);
    }

    @Transactional
    public void saveSettings(TenantId tenantId, RetentionSettings settings) {
        if (settings.conversationMonths() < 1 || settings.conversationMonths() > 120
                || settings.offboardPurgeDays() < 1 || settings.offboardPurgeDays() > 365) {
            throw new IllegalArgumentException("Out of range");
        }
        retention.save(tenantId, settings, clock.instant());
        audit.record(AuditEvent.of(tenantId, "retention.updated", "tenant", tenantId.value())
                .withAfter(Map.of("conversationMonths", settings.conversationMonths(),
                        "offboardPurgeDays", settings.offboardPurgeDays())));
    }

    @Scheduled(cron = "0 23 2 * * *", zone = "Africa/Nairobi")
    void scheduled() {
        if (!enabled) {
            return;
        }
        try {
            runRetention();
        } catch (RuntimeException e) {
            log.error("Retention run failed", e);
        }
    }

    /** Daily: old conversations per tenant, offboarded tenants past their grace period, finished jobs. */
    public int runRetention() {
        Instant now = clock.instant();
        int deleted = 0;
        for (TenantView t : tenants.listAll()) {
            TenantId id = TenantId.of(t.id());
            RetentionSettings s = settings(id);
            if (t.status() == TenantStatus.OFFBOARDED) {
                Optional<Instant> since = tenants.offboardedAt(id);
                if (since.isPresent() && since.get().plus(Duration.ofDays(s.offboardPurgeDays())).isBefore(now)) {
                    deleted += purgeTenant(id);
                }
                continue;
            }
            Instant cutoff = now.atZone(ZoneId.of(t.timezone())).minusMonths(s.conversationMonths()).toInstant();
            int batch;
            do {
                batch = conversations.purgeInactiveSince(id, cutoff);
                deleted += batch;
            } while (batch > 0);
        }
        jobs.purgeFinishedBefore(now.minus(JOB_RETENTION));
        return deleted;
    }

    /** Offboarding purge (NFR-11): keeps the tenant row, usage, audit log and (later) payment records. */
    @Transactional
    public int purgeTenant(TenantId tenantId) {
        bookings.purgeTenant(tenantId);
        int conversationsDeleted = conversations.purgeTenant(tenantId);
        knowledge.purgeTenant(tenantId);
        evaluation.purgeTenant(tenantId);
        templates.purgeTenant(tenantId);
        whatsapp.purgeTenant(tenantId);
        changeRequests.purgeTenant(tenantId);
        if (conversationsDeleted > 0) {
            audit.record(AuditEvent.of(tenantId, "tenant.purged", "tenant", tenantId.value()));
        }
        return conversationsDeleted;
    }
}
