package ai.zamu.audit;

import java.util.Objects;
import java.util.UUID;

import ai.zamu.shared.ids.TenantId;

/**
 * One audited action (NFR-14). {@code before} and {@code after} are serialised to JSON; pass small view
 * records, never secrets (NFR-06).
 *
 * @param tenantId    the tenant affected; null for platform-level actions
 * @param actorUserId who did it; null means "the logged-in user", filled in by {@link AuditService}
 */
public record AuditEvent(TenantId tenantId, UUID actorUserId, String action, String entity, UUID entityId,
        Object before, Object after) {

    public AuditEvent {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(entity, "entity");
    }

    public static AuditEvent of(TenantId tenantId, String action, String entity, UUID entityId) {
        return new AuditEvent(tenantId, null, action, entity, entityId, null, null);
    }

    public AuditEvent withBefore(Object value) {
        return new AuditEvent(tenantId, actorUserId, action, entity, entityId, value, after);
    }

    public AuditEvent withAfter(Object value) {
        return new AuditEvent(tenantId, actorUserId, action, entity, entityId, before, value);
    }

    public AuditEvent withActor(UUID userId) {
        return new AuditEvent(tenantId, userId, action, entity, entityId, before, after);
    }
}
