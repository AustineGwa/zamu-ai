package ai.zamu.shared.security;

import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Starts an authenticated session once a user has passed every login step. Rotates the session ID first to
 * prevent session fixation, then stores the security context in the (MySQL-backed) session.
 */
@Component
public class SessionLogin {

    private final SecurityContextRepository securityContextRepository;
    private final SecurityContextHolderStrategy holderStrategy = SecurityContextHolder.getContextHolderStrategy();

    SessionLogin(SecurityContextRepository securityContextRepository) {
        this.securityContextRepository = securityContextRepository;
    }

    public void login(ZamuPrincipal principal, HttpServletRequest request, HttpServletResponse response) {
        if (request.getSession(false) != null) {
            request.changeSessionId();
        }
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal, null, List.of(new SimpleGrantedAuthority(principal.role().authority())));
        SecurityContext context = holderStrategy.createEmptyContext();
        context.setAuthentication(authentication);
        holderStrategy.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }
}
