package ai.zamu.tenants;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ai.zamu.audit.AuditEvent;
import ai.zamu.audit.AuditService;
import ai.zamu.shared.ids.TenantId;
import ai.zamu.shared.ids.UuidV7;
import ai.zamu.shared.web.NotFoundException;

/**
 * Owner change requests (spec: Settings "as change requests"; Knowledge base "suggest a change"; REP-02 "suggest KB
 * entry"; admin Change requests screen). Owners ask; the Zamu team applies the change and closes the request.
 */
@Service
public class ChangeRequestService {

    static final int MAX_DETAILS = 2000;

    private final ChangeRequestRepository repository;
    private final AuditService audit;
    private final Clock clock;

    ChangeRequestService(ChangeRequestRepository repository, AuditService audit, Clock clock) {
        this.repository = repository;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public UUID submit(TenantId tenantId, UUID userId, ChangeRequestType type, String details) {
        String text = details == null ? "" : details.strip();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("Describe the change");
        }
        if (text.length() > MAX_DETAILS) {
            text = text.substring(0, MAX_DETAILS);
        }
        UUID id = UuidV7.next(clock);
        repository.insert(tenantId, id, userId, type, text, clock.instant());
        audit.record(AuditEvent.of(tenantId, "change_request.submitted", "change_request", id)
                .withAfter(Map.of("type", type.name())));
        return id;
    }

    @Transactional(readOnly = true)
    public List<ChangeRequest> forTenant(TenantId tenantId) {
        return repository.findForTenant(tenantId, 50);
    }

    @Transactional(readOnly = true)
    public List<ChangeRequest> queue(boolean openOnly) {
        return repository.findAll(openOnly, 200);
    }

    @Transactional(readOnly = true)
    public int openCount() {
        return repository.countOpen();
    }

    /** Admin console: marks the request done or declined, with an optional note for the owner. */
    @Transactional
    public void close(UUID requestId, boolean done, UUID handledBy, String note) {
        ChangeRequest request = repository.findById(requestId)
                .orElseThrow(() -> new NotFoundException("Change request not found"));
        TenantId tenantId = TenantId.of(request.tenantId());
        String status = done ? "done" : "declined";
        String cleanNote = note == null || note.isBlank() ? null : note.strip();
        if (cleanNote != null && cleanNote.length() > 500) {
            cleanNote = cleanNote.substring(0, 500);
        }
        if (repository.close(tenantId, requestId, status, handledBy, cleanNote, clock.instant()) == 0) {
            return; // already closed
        }
        audit.record(AuditEvent.of(tenantId, "change_request." + status, "change_request", requestId));
    }

    /** NFR-11: offboarding purge. */
    @Transactional
    public void purgeTenant(TenantId tenantId) {
        repository.deleteAllForTenant(tenantId);
    }
}
