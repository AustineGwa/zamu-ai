package ai.zamu.shared.security;

import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import ai.zamu.shared.ids.TenantId;

/**
 * The only place an admin route may turn a tenant ID from the URL into a {@link TenantId} (spec: Web routes,
 * "admin routes carry an explicit tenant ID and require the operator or admin role"; NFR-09).
 */
@Component
public class AdminTenantScope {

    public TenantId forPath(UUID tenantIdFromPath) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof ZamuPrincipal principal)
                || !principal.isPlatformStaff()) {
            throw new AccessDeniedException("Operator or admin role required");
        }
        if (tenantIdFromPath == null) {
            throw new AccessDeniedException("Missing tenant");
        }
        return TenantId.of(tenantIdFromPath);
    }
}
