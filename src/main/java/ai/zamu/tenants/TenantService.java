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
