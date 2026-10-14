package ai.zamu.shared.security;

import org.springframework.core.MethodParameter;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import ai.zamu.shared.ids.TenantId;

/**
 * Resolves a {@link TenantId} controller parameter from the logged-in user's session, never from the request
 * (NFR-09). A tenant-facing controller just declares {@code TenantId tenantId} as a parameter.
 */
class CurrentTenantResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return TenantId.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        return currentTenant();
    }

    static TenantId currentTenant() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof ZamuPrincipal principal
                && principal.tenantId() != null) {
            return principal.tenantId();
        }
        throw new AccessDeniedException("No tenant in session");
    }
}
