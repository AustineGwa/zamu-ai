package ai.zamu.handovers;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ai.zamu.audit.AuditEvent;
import ai.zamu.audit.AuditService;
import ai.zamu.bookings.BusinessHoursService;
import ai.zamu.shared.ids.TenantId;
import ai.zamu.shared.ids.UuidV7;
import ai.zamu.shared.web.LiveUpdates;
import ai.zamu.shared.web.NotFoundException;

/**
 * Handovers from Zamu to staff (spec HO-01 to HO-07). The conversation itself (status, assignee) belongs to the
 * conversations package: requests come from its engine, and accept, release and close are published as
 * {@link HandoverEvents} that it applies.
 */
@Service
public class HandoverService {

    private final HandoverRepository repository;
    private final BusinessHoursService hours;
    private final LiveUpdates live;
    private final ApplicationEventPublisher events;
    private final AuditService audit;
    private final Clock clock;

    HandoverService(HandoverRepository repository, BusinessHoursService hours, LiveUpdates live,
            ApplicationEventPublisher events, AuditService audit, Clock clock) {
        this.repository = repository;
        this.hours = hours;
        this.live = live;
        this.events = events;
        this.audit = audit;
        this.clock = clock;
    }

    /**
     * Opens a handover unless one is already open, notifies staff in the dashboard (HO-03), and says whether staff
     * are on duty so the right message goes to the customer (HO-02).
     */
    @Transactional
    public HandoverOutcome request(TenantId tenantId, UUID conversationId, HandoverReason reason) {
        Instant now = clock.instant();
        if (repository.findOpen(tenantId, conversationId).isEmpty()) {
            repository.insert(tenantId, UuidV7.next(clock), conversationId, reason, now, null);
            // Only the conversation ID goes to browsers; notifications never contain customer details.
            live.publish(tenantId, "handover", conversationId.toString());
        }
        if (hours.handoverOpen(tenantId, now)) {
            return new HandoverOutcome(true, null);
        }
        return new HandoverOutcome(false, hours.nextHandoverOpening(tenantId, now).orElse(null));
    }

    /** A staff member replied to a chat Zamu was handling: the chat becomes theirs. */
    @Transactional
    public void staffTakeover(TenantId tenantId, UUID conversationId, UUID userId) {
        Optional<Handover> open = repository.findOpen(tenantId, conversationId);
        if (open.isEmpty()) {
            repository.insert(tenantId, UuidV7.next(clock), conversationId, HandoverReason.STAFF_INITIATED,
                    clock.instant(), userId);
        } else if (open.get().acceptedBy() == null) {
            repository.accept(tenantId, open.get().id(), userId, clock.instant());
        }
        events.publishEvent(new HandoverEvents.Accepted(tenantId, conversationId, userId));
    }

    /** HO-05: a staff member accepts the handover; others see who has it. */
    @Transactional
    public void accept(TenantId tenantId, UUID conversationId, UUID userId) {
        Handover open = repository.findOpen(tenantId, conversationId)
                .orElseThrow(() -> new NotFoundException("No open handover for this conversation"));
        repository.accept(tenantId, open.id(), userId, clock.instant());
        events.publishEvent(new HandoverEvents.Accepted(tenantId, conversationId, userId));
        audit.record(AuditEvent.of(tenantId, "handover.accepted", "conversation", conversationId));
        live.publish(tenantId, "inbox", conversationId.toString());
    }

    /** HO-06: hand the chat back to Zamu. */
    @Transactional
    public void release(TenantId tenantId, UUID conversationId) {
        repository.resolveOpen(tenantId, conversationId, clock.instant());
        events.publishEvent(new HandoverEvents.Released(tenantId, conversationId));
        audit.record(AuditEvent.of(tenantId, "handover.released", "conversation", conversationId));
        live.publish(tenantId, "inbox", conversationId.toString());
    }

    /** HO-06: close the chat. */
    @Transactional
    public void close(TenantId tenantId, UUID conversationId) {
        repository.resolveOpen(tenantId, conversationId, clock.instant());
        events.publishEvent(new HandoverEvents.Closed(tenantId, conversationId));
        audit.record(AuditEvent.of(tenantId, "conversation.closed", "conversation", conversationId));
        live.publish(tenantId, "inbox", conversationId.toString());
    }

    /** Resolves any open handover on a conversation that a newer conversation has replaced (MSG-04). */
    @Transactional
    public void resolveSuperseded(TenantId tenantId, UUID conversationId) {
        repository.resolveOpen(tenantId, conversationId, clock.instant());
    }

    /** First staff reply time: stops the 30-minute escalation (HO-06) and feeds the first-reply report. */
    @Transactional
    public void recordStaffReply(TenantId tenantId, UUID conversationId, Instant at) {
        repository.recordFirstStaffReply(tenantId, conversationId, at);
    }

    @Transactional(readOnly = true)
    public Optional<HandoverView> current(TenantId tenantId, UUID conversationId) {
        return repository.findOpen(tenantId, conversationId).map(Handover::toView);
    }

    @Transactional(readOnly = true)
    public int openCount(TenantId tenantId) {
        return repository.countOpen(tenantId);
    }

    /** NFR-11, NFR-12: removes the handovers of these conversations (privacy deletion and retention). */
    @Transactional
    public int deleteForConversations(TenantId tenantId, java.util.List<UUID> conversationIds) {
        return repository.deleteForConversations(tenantId, conversationIds);
    }

    /** NFR-12: the handovers of these conversations, for a customer's data export. */
    @Transactional(readOnly = true)
    public java.util.List<java.util.Map<String, Object>> exportForConversations(TenantId tenantId,
            java.util.List<UUID> conversationIds) {
        return repository.exportForConversations(tenantId, conversationIds);
    }

    /** NFR-11: offboarding purge. */
    @Transactional
    public int deleteAllForTenant(TenantId tenantId) {
        return repository.deleteAllForTenant(tenantId);
    }
}
