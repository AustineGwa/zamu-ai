package ai.zamu.shared.security;

import java.util.List;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Registers the session-based {@code TenantId} argument resolver (NFR-09). */
@Configuration(proxyBeanMethods = false)
class SecurityWebConfig implements WebMvcConfigurer {

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new CurrentTenantResolver());
    }
}
