package ai.zamu.shared.security;

import java.util.Optional;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Reads the logged-in user outside controllers (services, audit). */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static Optional<ZamuPrincipal> get() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof ZamuPrincipal principal) {
            return Optional.of(principal);
        }
        return Optional.empty();
    }
}
