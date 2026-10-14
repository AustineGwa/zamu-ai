package ai.zamu.shared.security;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import org.springframework.security.core.AuthenticatedPrincipal;

import ai.zamu.shared.ids.TenantId;

/**
 * The logged-in dashboard or console user, stored in the MySQL-backed session.
 *
 * <p>{@link #getName()} returns the user ID, which Spring Session indexes as PRINCIPAL_NAME so that all of a
 * user's sessions can be deleted the moment an owner disables them. It is deliberately not the email
 * address, so no personal data lands in that index.
 *
 * @param tenantId null for Zamu operators and admins
 */
public record ZamuPrincipal(UUID userId, TenantId tenantId, ZamuRole role, String email, String name)
        implements AuthenticatedPrincipal, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public ZamuPrincipal {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(role, "role");
        if (role.isPlatformStaff() && tenantId != null) {
            throw new IllegalArgumentException("Zamu team users do not belong to a tenant");
        }
        if (!role.isPlatformStaff() && tenantId == null) {
            throw new IllegalArgumentException("Tenant users must belong to a tenant");
        }
    }

    @Override
    public String getName() {
        return userId.toString();
    }

    public boolean isPlatformStaff() {
        return role.isPlatformStaff();
    }

    public boolean isOwner() {
        return role == ZamuRole.OWNER;
    }
}
