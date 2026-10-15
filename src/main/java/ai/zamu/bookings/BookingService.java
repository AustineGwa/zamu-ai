package ai.zamu.bookings;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import ai.zamu.audit.AuditEvent;
import ai.zamu.audit.AuditService;
import ai.zamu.shared.ids.TenantId;
import ai.zamu.shared.ids.UuidV7;
import ai.zamu.shared.jobs.JobQueue;
import ai.zamu.shared.jobs.NewJob;
import ai.zamu.shared.web.NotFoundException;

/**
 * Bookings (spec: Bookings, calendar and reminders). The public API the AI dialogue, the staff pages and payments
 * use. Holds and moves take a per-tenant lock and re-check the slot with fresh calendar data, so the same time is
 * never given twice (BK-05); calendar events are written after the database commits.
 */
@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    static final String REMINDER_JOB = "booking_reminder";
    /** Spec: a held slot is kept for 10 minutes while the customer confirms or pays. */
    static final Duration HOLD = Duration.ofMinutes(10);
    /**
     * ASSUMPTION: until M-Pesa payments are switched on for the tenant, a booking waiting for its deposit is kept
     * for 2 hours so staff can arrange payment and confirm it.
     */
    static final Duration MANUAL_DEPOSIT_HOLD = Duration.ofHours(2);
    /** Numbered replies ("1 to reschedule") refer to a confirmation or reminder for this long. */
    static final Duration AWAIT_REPLY = Duration.ofHours(48);

    /** What a hold needs (BK-14: name, phone and service only). */
    public record HoldRequest(UUID customerId, String customerName, String customerPhone, UUID conversationId,
            UUID serviceId, UUID resourceId, Instant start, String source, String notes) {
    }

    private final BookingRepository bookings;
    private final BookingCatalogRepository catalog;
    private final SlotFinder slots;
    private final CalendarGateway calendar;
    private final CalendarConnectionRepository connections;
    private final JobQueue jobs;
    private final ApplicationEventPublisher events;
    private final AuditService audit;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final boolean schedulerEnabled;

    BookingService(BookingRepository bookings, BookingCatalogRepository catalog, SlotFinder slots,
            CalendarGateway calendar, CalendarConnectionRepository connections, JobQueue jobs,
            ApplicationEventPublisher events, AuditService audit, PlatformTransactionManager transactions, Clock clock,
            @Value("${zamu.jobs.enabled:true}") boolean schedulerEnabled) {
        this.bookings = bookings;
        this.catalog = catalog;
        this.slots = slots;
        this.calendar = calendar;
        this.connections = connections;
        this.jobs = jobs;
        this.events = events;
        this.audit = audit;
        this.tx = new TransactionTemplate(transactions);
        this.clock = clock;
        this.schedulerEnabled = schedulerEnabled;
    }

    // --- reading ---

    @Transactional(readOnly = true)
    public List<BookableService> services(TenantId tenantId) {
        return catalog.findServices(tenantId, true);
    }

    @Transactional(readOnly = true)
    public Optional<BookableService> service(TenantId tenantId, UUID serviceId) {
        return catalog.findService(tenantId, serviceId).filter(BookableService::active);
    }

    @Transactional(readOnly = true)
    public BookingSettings settings(TenantId tenantId) {
        return catalog.findSettings(tenantId).orElse(BookingSettings.DEFAULTS);
    }

    @Transactional(readOnly = true)
    public Booking get(TenantId tenantId, UUID bookingId) {
        return bookings.findById(tenantId, bookingId).orElseThrow(() -> new NotFoundException("Booking not found"));
    }

    @Transactional(readOnly = true)
    public Optional<Booking> find(TenantId tenantId, UUID bookingId) {
        return bookings.findById(tenantId, bookingId);
    }

    @Transactional(readOnly = true)
    public List<Booking> between(TenantId tenantId, Instant from, Instant to, boolean includeCancelled) {
        return bookings.findBetween(tenantId, from, to, includeCancelled);
    }

    @Transactional(readOnly = true)
    public List<Booking> forCustomer(TenantId tenantId, UUID customerId, int limit) {
        return bookings.findForCustomer(tenantId, customerId, limit);
    }

    @Transactional(readOnly = true)
    public List<Booking> upcomingForCustomer(TenantId tenantId, UUID customerId) {
        return bookings.findUpcomingForCustomer(tenantId, customerId, clock.instant());
    }

    /** The booking that a numbered reply ("1", "2", "3") refers to, if a confirmation or reminder is open. */
    @Transactional(readOnly = true)
    public Optional<Booking> awaitingReply(TenantId tenantId, UUID customerId) {
        return bookings.findAwaiting(tenantId, customerId, clock.instant());
    }

    @Transactional
    public void clearAwaiting(TenantId tenantId, UUID bookingId) {
        bookings.setAwaiting(tenantId, bookingId, null, null);
    }

    /** BK-08: customers may change or cancel until the cut-off before the appointment. */
    public boolean changeAllowed(TenantId tenantId, Booking booking) {
        Instant cutoff = booking.startsAt().minus(Duration.ofMinutes(settings(tenantId).changeCutoffMin()));
        return clock.instant().isBefore(cutoff);
    }

    /**
     * Nearest free slots (BK-03).
     *
     * @throws CalendarUnavailableException when the calendar can't be checked
     */
    @Transactional(readOnly = true)
    public List<Slot> slots(TenantId tenantId, UUID serviceId, int limit, UUID movingBookingId) {
        BookableService service = service(tenantId, serviceId).orElseThrow(() -> new NotFoundException("Service not found"));
        return slots.find(tenantId, service, limit, movingBookingId, null, false);
    }

    /** All free slots on one day, for the staff slot picker. */
    @Transactional(readOnly = true)
    public List<Slot> slotsOn(TenantId tenantId, UUID serviceId, LocalDate day, UUID movingBookingId) {
        BookableService service = service(tenantId, serviceId).orElseThrow(() -> new NotFoundException("Service not found"));
        return slots.find(tenantId, service, 64, movingBookingId, day, false);
    }

    // --- changing ---

    /**
     * Takes the slot (BK-05). Without a deposit the booking is confirmed at once (BK-06); with one it waits for
     * payment, and {@link BookingEvents.DepositNeeded} is published.
     *
     * @throws BookingSlotTakenException when the time is no longer free
     */
    public Booking hold(TenantId tenantId, HoldRequest request) {
        Booking booking = tx.execute(status -> {
            Instant now = clock.instant();
            catalog.lockTenant(tenantId, now);
            BookableService service = service(tenantId, request.serviceId())
                    .orElseThrow(() -> new NotFoundException("Service not found"));
            Slot slot = slots.check(tenantId, service, request.resourceId(), request.start(), null);
            if (slot == null) {
                throw new BookingSlotTakenException();
            }
            UUID id = UuidV7.next(clock);
            boolean deposit = service.needsDeposit();
            bookings.insert(tenantId, new BookingRepository.NewBooking(id, request.customerId(), request.customerName(),
                    request.customerPhone(), request.conversationId(), service.id(), slot.resourceId(), slot.startsAt(),
                    slot.endsAt(), deposit ? BookingStatus.PENDING_PAYMENT : BookingStatus.CONFIRMED,
                    deposit ? now.plus(MANUAL_DEPOSIT_HOLD) : null, request.source(), request.notes()), now);
            Booking created = bookings.findById(tenantId, id).orElseThrow();
            audit.record(AuditEvent.of(tenantId, "booking.created", "booking", id)
                    .withAfter(Map.of("status", created.status().name(), "source", request.source())));
            if (deposit) {
                events.publishEvent(new BookingEvents.DepositNeeded(created));
            } else {
                afterConfirm(tenantId, created, "staff".equals(request.source()));
            }
            return created;
        });
        String eventId = calendar.createEvent(tenantId, booking, booking.status() != BookingStatus.CONFIRMED);
        if (eventId != null && !eventId.isBlank()) {
            bookings.setCalendarEvent(tenantId, booking.id(), eventId);
        }
        return bookings.findById(tenantId, booking.id()).orElse(booking);
    }

    /** Confirms a booking that was waiting for its deposit: after M-Pesa confirms, or staff record payment. */
    public Booking confirm(TenantId tenantId, UUID bookingId, boolean notifyCustomer) {
        Booking booking = tx.execute(status -> {
            Booking b = get(tenantId, bookingId);
            if (b.status() == BookingStatus.CONFIRMED) {
                return b;
            }
            if (b.status() != BookingStatus.HELD && b.status() != BookingStatus.PENDING_PAYMENT) {
                throw new BookingRuleException("Only a booking waiting for confirmation can be confirmed.");
            }
            bookings.updateStatus(tenantId, bookingId, BookingStatus.CONFIRMED, null, clock.instant());
            Booking confirmed = get(tenantId, bookingId);
            audit.record(AuditEvent.of(tenantId, "booking.confirmed", "booking", bookingId));
            afterConfirm(tenantId, confirmed, notifyCustomer);
            return confirmed;
        });
        calendar.updateEvent(tenantId, booking, false); // BK-07
        return booking;
    }

    private void afterConfirm(TenantId tenantId, Booking booking, boolean notifyCustomer) {
        bookings.setAwaiting(tenantId, booking.id(), "confirmation", clock.instant().plus(AWAIT_REPLY));
        scheduleReminders(tenantId, booking);
        events.publishEvent(new BookingEvents.Confirmed(booking, notifyCustomer));
    }

    /** BK-11, BK-12: 24 hours before (and 2 hours before if enabled), not for bookings made under 3 hours ahead. */
    private void scheduleReminders(TenantId tenantId, Booking b) {
        Instant now = clock.instant();
        if (Duration.between(b.createdAt(), b.startsAt()).compareTo(Duration.ofHours(3)) < 0) {
            return;
        }
        Instant day = b.startsAt().minus(Duration.ofHours(24));
        if (day.isAfter(now)) {
            jobs.enqueue(reminderJob(tenantId, b, "24h", day));
        }
        Instant twoHours = b.startsAt().minus(Duration.ofHours(2));
        if (settings(tenantId).reminder2h() && twoHours.isAfter(now)) {
            jobs.enqueue(reminderJob(tenantId, b, "2h", twoHours));
        }
    }

    private static NewJob reminderJob(TenantId tenantId, Booking b, String kind, Instant at) {
        return NewJob.of(REMINDER_JOB, tenantId, Map.of("bookingId", b.id().toString(), "kind", kind,
                "startsAt", b.startsAt().toString())).runAt(at).lockKey("booking:" + b.id());
    }

    /** Called by the reminder job: sends only if the booking is still confirmed at the same time. */
    void remind(TenantId tenantId, UUID bookingId, String kind, Instant expectedStart) {
        tx.executeWithoutResult(status -> bookings.findById(tenantId, bookingId)
                .filter(b -> b.status() == BookingStatus.CONFIRMED && b.startsAt().equals(expectedStart)
                        && b.startsAt().isAfter(clock.instant()))
                .ifPresent(b -> {
                    bookings.setAwaiting(tenantId, b.id(), "reminder", b.startsAt());
                    events.publishEvent(new BookingEvents.ReminderDue(b, kind));
                }));
    }

    /**
     * Cancels a booking.
     *
     * @param by customer, staff, system or calendar; customers are told in the chat, others by message
     */
    public Booking cancel(TenantId tenantId, UUID bookingId, String by) {
        Booking cancelled = tx.execute(status -> {
            Booking b = get(tenantId, bookingId);
            if (!b.status().open()) {
                return null;
            }
            bookings.updateStatus(tenantId, bookingId, BookingStatus.CANCELLED, by, clock.instant());
            Booking after = get(tenantId, bookingId);
            audit.record(AuditEvent.of(tenantId, "booking.cancelled", "booking", bookingId)
                    .withAfter(Map.of("by", by)));
            events.publishEvent(new BookingEvents.Cancelled(after, by, !"customer".equals(by)));
            return after;
        });
        if (cancelled == null) {
            return get(tenantId, bookingId);
        }
        if (!"calendar".equals(by)) {
            calendar.deleteEvent(tenantId, cancelled.calendarEventId());
        }
        return cancelled;
    }

    /**
     * Moves a booking to a new free time (BK-08 by the customer, BK-09 by staff).
     *
     * @throws BookingSlotTakenException when the new time isn't free
     */
    public Booking move(TenantId tenantId, UUID bookingId, UUID resourceId, Instant start, String by) {
        Booking moved = tx.execute(status -> {
            Instant now = clock.instant();
            catalog.lockTenant(tenantId, now);
            Booking b = get(tenantId, bookingId);
            if (!b.status().open()) {
                throw new BookingRuleException("This booking can no longer be moved.");
            }
            BookableService service = catalog.findService(tenantId, b.serviceId())
                    .orElseThrow(() -> new NotFoundException("Service not found"));
            Slot slot = slots.check(tenantId, service, resourceId, start, b.id());
            if (slot == null) {
                throw new BookingSlotTakenException();
            }
            bookings.updateTimes(tenantId, bookingId, slot.resourceId(), slot.startsAt(), slot.endsAt(), now);
            Booking after = get(tenantId, bookingId);
            audit.record(AuditEvent.of(tenantId, "booking.moved", "booking", bookingId).withAfter(Map.of("by", by)));
            if (after.status() == BookingStatus.CONFIRMED) {
                bookings.setAwaiting(tenantId, bookingId, "confirmation", now.plus(AWAIT_REPLY));
                scheduleReminders(tenantId, after);
            }
            events.publishEvent(new BookingEvents.Moved(after, by, !"customer".equals(by)));
            return after;
        });
        calendar.updateEvent(tenantId, moved, moved.status() != BookingStatus.CONFIRMED);
        return moved;
    }

    /** BK-10: the event was moved in the calendar; follow it without re-checking availability. */
    private void followCalendarMove(TenantId tenantId, Booking b, Instant start, Instant end) {
        tx.executeWithoutResult(status -> {
            bookings.updateTimes(tenantId, b.id(), b.resourceId(), start, end, clock.instant());
            Booking after = get(tenantId, b.id());
            audit.record(AuditEvent.of(tenantId, "booking.moved", "booking", b.id()).withAfter(Map.of("by", "calendar")));
            if (after.status() == BookingStatus.CONFIRMED) {
                scheduleReminders(tenantId, after);
            }
            events.publishEvent(new BookingEvents.Moved(after, "calendar", true));
        });
    }

    /** BK-13: completed or no-show, after the appointment time. */
    @Transactional
    public void markOutcome(TenantId tenantId, UUID bookingId, BookingStatus outcome) {
        if (outcome != BookingStatus.COMPLETED && outcome != BookingStatus.NO_SHOW) {
            throw new BookingRuleException("Choose completed or no-show.");
        }
        Booking b = get(tenantId, bookingId);
        if (b.status() != BookingStatus.CONFIRMED && b.status() != BookingStatus.COMPLETED
                && b.status() != BookingStatus.NO_SHOW) {
            throw new BookingRuleException("Only confirmed bookings can be marked completed or no-show.");
        }
        if (b.startsAt().isAfter(clock.instant())) {
            throw new BookingRuleException("You can mark this after the appointment starts.");
        }
        bookings.updateStatus(tenantId, bookingId, outcome, null, clock.instant());
        audit.record(AuditEvent.of(tenantId, "booking." + outcome.name().toLowerCase(java.util.Locale.ROOT), "booking",
                bookingId));
    }

    // --- background ---

    @Scheduled(fixedDelay = 60_000, initialDelay = 45_000)
    void scheduledExpiry() {
        if (!schedulerEnabled) {
            return;
        }
        try {
            expireHolds();
        } catch (RuntimeException e) {
            log.error("Hold expiry sweep failed", e);
        }
    }

    /** Releases held and unpaid bookings whose time ran out (spec: Booking states). Returns how many. */
    public int expireHolds() {
        int released = 0;
        for (Booking b : bookings.findExpiredHolds(clock.instant(), 100)) {
            cancel(TenantId.of(b.tenantId()), b.id(), "system");
            released++;
        }
        return released;
    }

    @Scheduled(fixedDelay = 300_000, initialDelay = 120_000)
    void scheduledSync() {
        if (!schedulerEnabled) {
            return;
        }
        try {
            syncCalendars();
        } catch (RuntimeException e) {
            log.error("Calendar sync failed", e);
        }
    }

    /**
     * BK-10: every 5 minutes, compares upcoming bookings with their calendar events. An event deleted or cancelled
     * in the calendar cancels the booking; a moved event moves it. Returns how many bookings changed.
     */
    public int syncCalendars() {
        int changed = 0;
        Instant now = clock.instant();
        for (UUID id : connections.findConnectedTenantIds()) {
            TenantId tenantId = TenantId.of(id);
            for (Booking b : bookings.findSyncCandidates(tenantId, now, now.plus(Duration.ofDays(60)))) {
                Optional<CalendarApi.EventState> event;
                try {
                    event = calendar.event(tenantId, b.calendarEventId());
                } catch (CalendarUnavailableException e) {
                    log.info("Calendar sync skipped for a tenant: {}", e.getMessage());
                    break;
                }
                if (event.isEmpty() || event.get().cancelled()) {
                    cancel(tenantId, b.id(), "calendar");
                    changed++;
                } else if (event.get().start() != null && event.get().end() != null
                        && (!event.get().start().equals(b.startsAt()) || !event.get().end().equals(b.endsAt()))) {
                    followCalendarMove(tenantId, b, event.get().start(), event.get().end());
                    changed++;
                }
            }
        }
        return changed;
    }

    // --- privacy (NFR-11, NFR-12) ---

    /** NFR-12: a customer's bookings for their data export (BK-14: service, time, status and notes only). */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> exportForCustomer(TenantId tenantId, UUID customerId) {
        return bookings.findForCustomer(tenantId, customerId, 1000).stream().map(b -> {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("service", b.serviceName());
            m.put("with", b.resourceName());
            m.put("starts_at", b.startsAt().toString());
            m.put("status", b.status().name().toLowerCase(java.util.Locale.ROOT));
            m.put("notes", b.notes());
            return m;
        }).toList();
    }

    /** NFR-12: deletes a customer's bookings; open ones are removed from the calendar too. */
    @Transactional
    public int deleteForCustomer(TenantId tenantId, UUID customerId) {
        bookings.findForCustomer(tenantId, customerId, 1000).stream().filter(b -> b.status().open())
                .forEach(b -> calendar.deleteEvent(tenantId, b.calendarEventId()));
        return bookings.deleteForCustomer(tenantId, customerId);
    }

    /** NFR-11: offboarding purge of bookings and booking setup. */
    @Transactional
    public void purgeTenant(TenantId tenantId) {
        bookings.deleteAllForTenant(tenantId);
        catalog.deleteAllForTenant(tenantId);
        calendar.forget(tenantId);
    }
}
