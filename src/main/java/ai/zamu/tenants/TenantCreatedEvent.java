package ai.zamu.tenants;

import ai.zamu.shared.ids.TenantId;

/** Published after a tenant is created, for features that set up per-tenant defaults. */
public record TenantCreatedEvent(TenantId tenantId) {
}
