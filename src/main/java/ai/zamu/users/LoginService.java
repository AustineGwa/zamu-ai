package ai.zamu.users;

import java.time.Clock;
import java.util.Locale;
import java.util.OptionalLong;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ai.zamu.audit.AuditEvent;
import ai.zamu.audit.AuditService;
import ai.zamu.shared.crypto.SecretCipher;
import ai.zamu.shared.crypto.Totp;
import ai.zamu.shared.ids.TenantId;
import ai.zamu.shared.ids.UuidV7;
import ai.zamu.shared.security.ZamuPrincipal;
import ai.zamu.shared.security.ZamuRole;

/**
 * Login completion and the Zamu team second factor (NFR-08). Package-private: only the login controller and the
 * admin bootstrap use it. Methods are public so transaction proxies apply.
 */
@Service
class LoginService {

    private static final Logger log = LoggerFactory.getLogger(LoginService.class);

    private final UserRepository users;
    private final AuditService audit;
    private final SecretCipher cipher;
    private final Clock clock;

    LoginService(UserRepository users, AuditService audit, SecretCipher cipher, Clock clock) {
        this.users = users;
        this.audit = audit;
        this.cipher = cipher;
        this.clock = clock;
    }

    /** Final step of every login: records it and returns the principal to store in the session. */
    @Transactional
    public ZamuPrincipal completeLogin(UUID userId) {
        User user = users.findActiveById(userId).orElseThrow(() -> new AccessDeniedException("User disabled"));
        users.recordLogin(userId, clock.instant());
        TenantId tenantId = user.tenantId() == null ? null : TenantId.of(user.tenantId());
        // The Operations Manual asks for every login in the audit log; NFR-14 does not list logins (see summary).
        audit.record(AuditEvent.of(tenantId, "user.login", "user", userId).withActor(userId));
        return user.toPrincipal();
    }

    @Transactional(readOnly = true)
    public boolean hasSecondFactor(UUID userId) {
        return users.findActiveById(userId).map(User::hasTotp).orElse(false);
    }

    /** First-time enrolment: the code proves the user typed the secret into their app correctly. */
    @Transactional
    public boolean enrolSecondFactor(UUID userId, String base32Secret, String code) {
        OptionalLong step = Totp.verify(base32Secret, code, clock.instant(), null);
        if (step.isEmpty()) {
            return false;
        }
        boolean saved = users.enableTotp(userId, cipher.encryptString(base32Secret), step.getAsLong(), clock.instant()) == 1;
        if (saved) {
            audit.record(AuditEvent.of(null, "user.second_factor_enabled", "user", userId).withActor(userId));
        }
        return saved;
    }

    @Transactional
    public boolean verifySecondFactor(UUID userId, String code) {
        User user = users.findActiveById(userId).orElse(null);
        if (user == null || !user.hasTotp()) {
            return false;
        }
        String secret = cipher.decryptString(user.totpSecret());
        OptionalLong step = Totp.verify(secret, code, clock.instant(), user.totpLastStep());
        return step.isPresent() && users.advanceTotpStep(userId, step.getAsLong());
    }

    @Transactional(readOnly = true)
    public String emailOf(UUID userId) {
        return users.findActiveById(userId).map(User::email).orElse("");
    }


    /** Creates the first Zamu admin if none exists (see AdminBootstrap). */
    @Transactional
    public void ensureBootstrapAdmin(String rawEmail, String name) {
        String email = rawEmail.trim().toLowerCase(Locale.ROOT);
        if (users.anyActiveWithRole(ZamuRole.ADMIN) || users.existsByEmail(email)) {
            return;
        }
        User admin = User.newUser(UuidV7.next(clock), null, email, name, ZamuRole.ADMIN, clock.instant());
        users.insertPlatformUser(admin);
        audit.record(AuditEvent.of(null, "user.bootstrap_admin_created", "user", admin.id())
                .withAfter(admin.toView()));
        log.info("Created the first Zamu admin");
    }
}
