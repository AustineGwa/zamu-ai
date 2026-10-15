package ai.zamu.shared.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

/**
 * Web security (NFR-07, NFR-08).
 *
 * <ul>
 *   <li>Login is email one-time code (users package), plus TOTP for Zamu team roles; there is no password form.</li>
 *   <li>/app/** is the client dashboard (staff, owner); /admin/** is the admin console (operator, admin).</li>
 *   <li>CSRF is on for every state-changing request except provider webhooks, which verify their own
 *       signatures or secrets (MSG-01, PAY-06).</li>
 *   <li>HSTS is sent on HTTPS requests by default; session cookies are secure, HTTP-only and same-site
 *       (application.yml).</li>
 * </ul>
 *
 * <p>Public (not package-private) only so that {@code @WebMvcTest} slices in feature packages can import it.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityContextRepository securityContextRepository)
            throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                // Public website: home page and sign-up (leads package).
                .requestMatchers("/", "/pricing", "/signup", "/signup/**").permitAll()
                .requestMatchers("/login", "/login/**", "/css/**", "/js/**", "/images/**", "/favicon.ico",
                        "/actuator/health", "/actuator/health/**", "/error").permitAll()
                .requestMatchers("/webhooks/**").permitAll()
                // Local h2 profile only: the WhatsApp simulator (controller exists only in that profile).
                .requestMatchers("/dev/**").authenticated()
                // Spec: admin console screens. Usage and billing, and the audit log, are for admins only.
                .requestMatchers("/admin/usage", "/admin/usage/**", "/admin/usage.csv", "/admin/audit", "/admin/audit/**")
                        .hasRole(ZamuRole.ADMIN.name())
                .requestMatchers("/admin/**").hasAnyRole(ZamuRole.OPERATOR.name(), ZamuRole.ADMIN.name())
                // Spec: Permissions. Manage staff users, reports and settings are owner-only in the dashboard.
                .requestMatchers("/app/users", "/app/users/**", "/app/settings", "/app/settings/**", "/app/reports",
                        "/app/reports/**", "/app/requests", "/app/requests/**")
                        .hasRole(ZamuRole.OWNER.name())
                .requestMatchers("/app/**").hasAnyRole(ZamuRole.STAFF.name(), ZamuRole.OWNER.name())
                .anyRequest().authenticated())
            .securityContext(context -> context.securityContextRepository(securityContextRepository))
            .csrf(csrf -> csrf.ignoringRequestMatchers("/webhooks/**"))
            .exceptionHandling(ex -> ex.authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/login")))
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable())
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?signedOut")
                .invalidateHttpSession(true)
                .deleteCookies("ZAMU_SESSION"))
            .headers(headers -> headers
                .contentSecurityPolicy(csp -> csp.policyDirectives(
                        "default-src 'self'; img-src 'self' data: https://images.unsplash.com; style-src 'self'; script-src 'self'; "
                                + "frame-ancestors 'none'; form-action 'self'; base-uri 'self'"))
                .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN)));
        return http.build();
    }
}
