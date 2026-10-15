package ai.zamu.whatsapp;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ai.zamu.audit.AuditEvent;
import ai.zamu.audit.AuditService;
import ai.zamu.conversations.TemplateCatalog;
import ai.zamu.shared.ids.TenantId;
import ai.zamu.shared.ids.UuidV7;
import ai.zamu.shared.web.NotFoundException;

/**
 * WhatsApp message templates per tenant: drafted in the admin console, submitted to Meta for review, approved or
 * rejected by Meta's template webhook. Approved templates are what staff can send outside the 24-hour window
 * (HO-07, MSG-14); this is the {@link TemplateCatalog} the conversations package uses.
 */
@Service
public class TemplateService implements TemplateCatalog {

    private static final Logger log = LoggerFactory.getLogger(TemplateService.class);

    private final WaTemplateRepository repository;
    private final WhatsAppAccountService accounts;
    private final WhatsAppApi api;
    private final AuditService audit;
    private final Clock clock;

    TemplateService(WaTemplateRepository repository, WhatsAppAccountService accounts, WhatsAppApi api,
            AuditService audit, Clock clock) {
        this.repository = repository;
        this.accounts = accounts;
        this.api = api;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TemplateOption> approved(TenantId tenantId) {
        return repository.findByStatus(tenantId, WaTemplateStatus.APPROVED).stream()
                .map(t -> new TemplateOption(t.name(), t.language(), t.body(), t.parameterCount()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<WaTemplateView> list(TenantId tenantId) {
        return repository.findAll(tenantId).stream().map(WaTemplate::toView).toList();
    }

    @Transactional
    public UUID create(TenantId tenantId, WaTemplateForm form) {
        if (!WaTemplate.placeholdersInOrder(form.body())) {
            throw new WaTemplateException("Number the placeholders {{1}}, {{2}} and so on, without gaps.");
        }
        if (repository.exists(tenantId, form.name(), form.language())) {
            throw new WaTemplateException("A template with this name and language already exists.");
        }
        UUID id = UuidV7.next(clock);
        repository.insert(tenantId, id, form, clock.instant());
        audit.record(AuditEvent.of(tenantId, "template.created", "wa_template", id)
                .withAfter(Map.of("name", form.name(), "language", form.language())));
        return id;
    }

    /** Sends a draft (or a rejected template after editing in Meta) for Meta's review. */
    @Transactional
    public void submit(TenantId tenantId, UUID templateId) {
        WaTemplate template = repository.findById(tenantId, templateId).orElseThrow(() -> new NotFoundException("Template not found"));
        if (template.status() == WaTemplateStatus.PENDING || template.status() == WaTemplateStatus.APPROVED) {
            throw new WaTemplateException("This template is already with Meta.");
        }
        Credentials credentials = accounts.credentials(tenantId)
                .orElseThrow(() -> new WaTemplateException("Connect WhatsApp first."));
        try {
            api.submitTemplate(credentials, template.name(), template.category(), template.language(), template.body(),
                    template.parameterCount());
        } catch (WhatsAppApiException e) {
            log.warn("Template submission failed ({})", e.code());
            throw new WaTemplateException("Meta did not accept the template (error " + e.code() + ").");
        }
        repository.updateStatus(tenantId, templateId, WaTemplateStatus.PENDING, null, clock.instant());
        audit.record(AuditEvent.of(tenantId, "template.submitted", "wa_template", templateId));
    }

    /**
     * Records Meta's decision by hand, for when the template webhook isn't subscribed yet or in local development.
     * ASSUMPTION: the spec doesn't describe this; Zamu team only.
     */
    @Transactional
    public void recordStatus(TenantId tenantId, UUID templateId, WaTemplateStatus status, String reason) {
        if (repository.updateStatus(tenantId, templateId, status, blankToNull(reason), clock.instant()) == 0) {
            throw new NotFoundException("Template not found");
        }
        audit.record(AuditEvent.of(tenantId, "template.status_recorded", "wa_template", templateId)
                .withAfter(Map.of("status", status.dbValue())));
    }

    /** From Meta's {@code message_template_status_update} webhook. Unknown templates are ignored. */
    @Transactional
    public void applyMetaStatus(TenantId tenantId, String name, String language, String event, String reason) {
        WaTemplateStatus status = WaTemplateStatus.fromMetaEvent(event);
        if (status == null || name.isBlank()) {
            return;
        }
        int updated = repository.updateStatusByName(tenantId, name, language, status, reason, clock.instant());
        if (updated > 0) {
            log.info("Template {} ({}) is now {}", name, language, status.dbValue());
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    /** NFR-11: offboarding purge. */
    @Transactional
    public void purgeTenant(TenantId tenantId) {
        repository.deleteAllForTenant(tenantId);
    }
}
