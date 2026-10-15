package ai.zamu.tenants;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ai.zamu.audit.AuditEvent;
import ai.zamu.audit.AuditService;
import ai.zamu.shared.ids.TenantId;
import ai.zamu.shared.ids.UuidV7;
import ai.zamu.shared.time.ZamuTime;

/** Tenants: create and configure client businesses (spec: Admin console, Tenants and Tenant setup wizard). */
@Service
public class TenantService {

    static final BigDecimal DEFAULT_CONFIDENCE_THRESHOLD = new BigDecimal("0.60");

    private final TenantRepository repository;
    private final AuditService audit;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    TenantService(TenantRepository repository, AuditService audit, ApplicationEventPublisher events, Clock clock) {
        this.repository = repository;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
    }

    /** Creates a tenant in onboarding status, with the plan's conversation limit. Admin console only. */
    @Transactional
    public TenantView create(CreateTenantRequest request) {
        String name = request.name().trim();
        if (repository.existsByNameExcluding(name, null)) {
            throw new DuplicateTenantNameException();
        }
        Instant now = clock.instant();
        Tenant tenant = new Tenant(
                UuidV7.next(clock),
                name,
                request.displayName().trim(),
                request.plan(),
                TenantStatus.ONBOARDING,
                ZamuTime.NAIROBI.getId(),
                TenantLanguage.EN,
                TenantTone.FRIENDLY,
                request.plan().conversationsPerMonth(),
                DEFAULT_CONFIDENCE_THRESHOLD,
                null,
                null,
                null,
                now,
                now);
        repository.insert(tenant);
        TenantView view = tenant.toView();
        audit.record(AuditEvent.of(tenant.tenantId(), "tenant.created", "tenant", tenant.id()).withAfter(view));
        events.publishEvent(new TenantCreatedEvent(tenant.tenantId()));
        return view;
    }

    @Transactional(readOnly = true)
    public List<TenantView> listAll() {
        return repository.findAll().stream().map(Tenant::toView).toList();
    }

    @Transactional(readOnly = true)
    public TenantView get(TenantId tenantId) {
        return repository.findById(tenantId).map(Tenant::toView).orElseThrow(TenantNotFoundException::new);
    }

    @Transactional
    public TenantView updateProfile(TenantId tenantId, TenantProfileForm form) {
        TenantView before = get(tenantId);
        String name = form.name().trim();
        if (repository.existsByNameExcluding(name, tenantId.value())) {
            throw new DuplicateTenantNameException();
        }
        repository.updateProfile(tenantId, name, form.displayName().trim(), form.plan(), form.conversationLimit(),
                clock.instant());
        TenantView after = get(tenantId);
        audit.record(AuditEvent.of(tenantId, "tenant.profile_updated", "tenant", tenantId.value())
                .withBefore(before).withAfter(after));
        return after;
    }

    @Transactional
    public TenantView updateTone(TenantId tenantId, TenantToneForm form) {
        TenantView before = get(tenantId);
        repository.updateTone(tenantId, form.tone(), form.defaultLanguage(), clock.instant());
        TenantView after = get(tenantId);
        audit.record(AuditEvent.of(tenantId, "tenant.tone_updated", "tenant", tenantId.value())
                .withBefore(before).withAfter(after));
        return after;
    }

    /**
     * Owner's staff-only switch (spec: Settings; MSG-09). Zamu stops replying, sends the holding reply once per
     * conversation and routes every chat to the inbox. Only a live tenant can switch to staff-only and back.
     */
    @Transactional
    public TenantView setStaffOnly(TenantId tenantId, boolean staffOnly) {
        TenantView before = get(tenantId);
        TenantStatus target = staffOnly ? TenantStatus.STAFF_ONLY : TenantStatus.LIVE;
        TenantStatus required = staffOnly ? TenantStatus.LIVE : TenantStatus.STAFF_ONLY;
        if (before.status() == target) {
            return before;
        }
        if (before.status() != required) {
            throw new IllegalTenantStatusException(before.status(), target);
        }
        repository.updateStatus(tenantId, target, clock.instant());
        TenantView after = get(tenantId);
        audit.record(AuditEvent.of(tenantId, staffOnly ? "tenant.staff_only_on" : "tenant.staff_only_off", "tenant",
                tenantId.value()).withBefore(before.status().name()).withAfter(after.status().name()));
        return after;
    }

    /** Setup wizard, tone step: AI-03 confidence threshold, AI-04 emergency number, HO-01 handover topics. */
    @Transactional
    public TenantView updateSafety(TenantId tenantId, TenantSafetyForm form) {
        TenantView before = get(tenantId);
        repository.updateSafety(tenantId, form.confidenceThreshold(), blankToNull(form.emergencyNumber()),
                blankToNull(form.handoverTopics()), clock.instant());
        TenantView after = get(tenantId);
        audit.record(AuditEvent.of(tenantId, "tenant.safety_updated", "tenant", tenantId.value())
                .withBefore(java.util.Map.of("threshold", before.confidenceThreshold().toPlainString()))
                .withAfter(java.util.Map.of("threshold", after.confidenceThreshold().toPlainString())));
        return after;
    }

    /** Owner or admin go-live (spec: setup wizard, go-live step): onboarding or paused becomes live. */
    @Transactional
    public TenantView goLive(TenantId tenantId) {
        return changeStatus(tenantId, TenantStatus.LIVE, java.util.Set.of(TenantStatus.ONBOARDING, TenantStatus.PAUSED),
                "tenant.went_live");
    }

    /** Admin pause: Zamu stops replying (MSG-09) until the tenant goes live again. */
    @Transactional
    public TenantView pause(TenantId tenantId) {
        return changeStatus(tenantId, TenantStatus.PAUSED,
                java.util.Set.of(TenantStatus.LIVE, TenantStatus.STAFF_ONLY, TenantStatus.ONBOARDING), "tenant.paused");
    }

    /**
     * Offboarding (NFR-11): Zamu stops for good; the tenant's data is deleted after the retention period, except
     * usage, audit and payment records. Only from paused.
     */
    @Transactional
    public TenantView offboard(TenantId tenantId) {
        return changeStatus(tenantId, TenantStatus.OFFBOARDED, java.util.Set.of(TenantStatus.PAUSED), "tenant.offboarded");
    }

    /** When an offboarded tenant was offboarded (its last status change), for the purge (NFR-11). */
    @Transactional(readOnly = true)
    public java.util.Optional<Instant> offboardedAt(TenantId tenantId) {
        return repository.findById(tenantId).filter(t -> t.status() == TenantStatus.OFFBOARDED).map(Tenant::updatedAt);
    }

    private TenantView changeStatus(TenantId tenantId, TenantStatus target, java.util.Set<TenantStatus> from,
            String action) {
        TenantView before = get(tenantId);
        if (before.status() == target) {
            return before;
        }
        if (!from.contains(before.status())) {
            throw new IllegalTenantStatusException(before.status(), target);
        }
        repository.updateStatus(tenantId, target, clock.instant());
        audit.record(AuditEvent.of(tenantId, action, "tenant", tenantId.value())
                .withBefore(before.status().name()).withAfter(target.name()));
        return get(tenantId);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    /** Saves the holding reply used in staff-only and paused mode; blank means the standard wording. */
    @Transactional
    public TenantView updateHoldingReply(TenantId tenantId, String holdingReply) {
        String value = holdingReply == null || holdingReply.isBlank() ? null : holdingReply.trim();
        repository.updateHoldingReply(tenantId, value, clock.instant());
        audit.record(AuditEvent.of(tenantId, "tenant.holding_reply_updated", "tenant", tenantId.value()));
        return get(tenantId);
    }

    /**
     * Maximum dashboard logins for the tenant's plan. ASSUMPTION: the Operations Manual's "Staff logins" cap
     * (2, 5, 15) counts owners and staff together; the specification has no requirement for it.
     */
    @Transactional(readOnly = true)
    public int loginLimit(TenantId tenantId) {
        return get(tenantId).plan().staffLogins();
    }

    /** Convenience for admin routes that already hold a raw ID from {@code AdminTenantScope}. */
    @Transactional(readOnly = true)
    public boolean exists(TenantId tenantId) {
        return repository.findById(tenantId).isPresent();
    }
}
