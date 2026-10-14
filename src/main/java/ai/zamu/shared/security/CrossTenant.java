package ai.zamu.shared.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a repository method that deliberately does not take a {@code TenantId} (NFR-09), for example login
 * lookups by email before the tenant is known, or Zamu admin listings across tenants.
 *
 * <p>The {@code RepositoryTenantGuardTest} fails the build for any repository method that has neither a
 * {@code TenantId} parameter nor this annotation. The reason is required so every exception is reviewed.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface CrossTenant {

    /** Why this query is safe without a tenant filter. */
    String value();
}
