package ai.zamu.users;

import java.time.Instant;
import java.util.UUID;

import ai.zamu.shared.ids.TenantId;
import ai.zamu.shared.security.ZamuPrincipal;
import ai.zamu.shared.security.ZamuRole;

/** A users row. {@code tenantId} is null for Zamu operators and admins. */
record User(
        UUID id,
        UUID tenantId,
        String email,
        String name,
        ZamuRole role,
        Instant lastLoginAt,
        Instant disabledAt,
        byte[] totpSecret,
        Instant totpEnabledAt,
        Long totpLastStep,
        Instant createdAt,
        Instant updatedAt) {

    boolean isActive() {
        return disabledAt == null;
    }

    boolean hasTotp() {
        return totpSecret != null && totpEnabledAt != null;
    }

    ZamuPrincipal toPrincipal() {
        return new ZamuPrincipal(id, tenantId == null ? null : TenantId.of(tenantId), role, email, name);
    }

    UserView toView() {
        return new UserView(id, email, name, role, lastLoginAt, disabledAt, createdAt);
    }

    static User newUser(UUID id, UUID tenantId, String email, String name, ZamuRole role, Instant now) {
        return new User(id, tenantId, email, name, role, null, null, null, null, null, now, now);
    }
}
