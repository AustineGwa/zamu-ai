package ai.zamu.audit;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import ai.zamu.shared.ids.TenantId;
import ai.zamu.shared.ids.UuidV7;
import ai.zamu.shared.security.CurrentUser;
import ai.zamu.shared.security.ZamuPrincipal;

import tools.jackson.databind.ObjectMapper;

/**
 * Writes the audit log (NFR-14): admin actions, settings changes, logins, exports, deletions, and tenant
 * dashboard access by Zamu team members. Runs in the caller's transaction, so the audit row commits or rolls back
 * with the change it describes.
 */
@Service
public class AuditService {

    private final AuditRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    AuditService(AuditRepository repository, ObjectMapper objectMapper, Clock clock) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public void record(AuditEvent event) {
        UUID actor = event.actorUserId() != null
                ? event.actorUserId()
                : CurrentUser.get().map(ZamuPrincipal::userId).orElse(null);
        repository.insert(new AuditEntry(
                UuidV7.next(clock),
                event.tenantId() == null ? null : event.tenantId().value(),
                actor,
                event.action(),
                event.entity(),
                event.entityId(),
                toJson(event.before()),
                toJson(event.after()),
                currentIp(),
                clock.instant()));
    }

    /** Recent actions in one tenant, for the owner's view of their own audit log. */
    @Transactional(readOnly = true)
    public List<AuditEntryView> recentForTenant(TenantId tenantId, int limit) {
        return repository.findRecentForTenant(tenantId, Math.min(limit, 500)).stream()
                .map(e -> new AuditEntryView(e.id(), e.actorUserId(), e.action(), e.entity(), e.entityId(), e.createdAt()))
                .toList();
    }

    private String toJson(Object value) {
        return value == null ? null : objectMapper.writeValueAsString(value);
    }

    private static String currentIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest().getRemoteAddr();
        }
        return null;
    }
}
