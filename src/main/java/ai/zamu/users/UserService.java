package ai.zamu.users;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ai.zamu.audit.AuditEvent;
import ai.zamu.audit.AuditService;
import ai.zamu.shared.ids.TenantId;
import ai.zamu.shared.ids.UuidV7;
import ai.zamu.tenants.TenantService;

/** Tenant dashboard users: list, add and disable logins. Login itself is in LoginService. */
@Service
public class UserService {

    private final UserRepository users;
    private final TenantService tenants;
    private final AuditService audit;
    private final FindByIndexNameSessionRepository<? extends Session> sessions;
    private final Clock clock;

    UserService(UserRepository users, TenantService tenants, AuditService audit,
            FindByIndexNameSessionRepository<? extends Session> sessions, Clock clock) {
        this.users = users;
        this.tenants = tenants;
        this.audit = audit;
        this.sessions = sessions;
        this.clock = clock;
    }

    // ---- Tenant users ------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<UserView> listForTenant(TenantId tenantId) {
        return users.findAllInTenant(tenantId).stream().map(User::toView).toList();
    }

    /**
     * Adds an owner or staff login to a tenant.
     *
     * @param enforcePlanLimit true when an owner adds staff from the dashboard; false for Zamu operators in
     *                         the setup wizard, who may go over the plan cap (ASSUMPTION, see TenantService)
     */
    @Transactional
    public UserView addTenantUser(TenantId tenantId, CreateUserRequest request, boolean enforcePlanLimit) {
        if (request.role().isPlatformStaff()) {
            throw new AccessDeniedException("Tenant users can only be staff or owners");
        }
        String email = LoginCodeService.normalise(request.email());
        if (users.existsByEmail(email)) {
            throw new DuplicateEmailException();
        }
        if (enforcePlanLimit) {
            int limit = tenants.loginLimit(tenantId);
            if (users.countActiveInTenant(tenantId) >= limit) {
                throw new StaffLimitReachedException(limit);
            }
        } else {
            tenants.get(tenantId); // 404 for an unknown tenant
        }
        User user = User.newUser(UuidV7.next(clock), tenantId.value(), email, request.name().trim(), request.role(),
                clock.instant());
        users.insertTenantUser(tenantId, user);
        UserView view = user.toView();
        audit.record(AuditEvent.of(tenantId, "user.created", "user", user.id()).withAfter(view));
        return view;
    }

    /** Disables a login and ends all of that user's sessions immediately (Operations Manual: Security). */
    @Transactional
    public void disable(TenantId tenantId, UUID userId, UUID actorUserId) {
        if (userId.equals(actorUserId)) {
            throw new CannotDisableSelfException();
        }
        User before = users.findInTenant(tenantId, userId).orElseThrow(UserNotFoundException::new);
        if (users.disableInTenant(tenantId, userId, clock.instant()) == 0) {
            return; // already disabled
        }
        endSessions(userId);
        audit.record(AuditEvent.of(tenantId, "user.disabled", "user", userId)
                .withBefore(before.toView())
                .withAfter(users.findInTenant(tenantId, userId).map(User::toView).orElse(null)));
    }

    private void endSessions(UUID userId) {
        Map<String, ? extends Session> active = sessions.findByPrincipalName(userId.toString());
        active.keySet().forEach(sessions::deleteById);
    }
}
