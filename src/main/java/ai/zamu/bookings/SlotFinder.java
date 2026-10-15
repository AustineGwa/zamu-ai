package ai.zamu.bookings;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import ai.zamu.shared.ids.TenantId;
import ai.zamu.tenants.TenantService;

/**
 * Free slots (BK-02, BK-03, BK-04): business hours and holidays, service duration plus buffer, minimum notice and
 * maximum advance, the resources that deliver the service, Zamu's own open bookings and the calendar's busy
 * times.
 *
 * <p>Calendar rule (ASSUMPTION): busy time on the tenant's main calendar blocks every resource; a resource with
 * its own calendar is also blocked by that calendar's busy time. Slots start every {@code slotStepMin} minutes
 * from opening time, and each start time is offered once, with the first free resource.
 */
@Component
class SlotFinder {

    private final BookingCatalogRepository catalog;
    private final BookingRepository bookings;
    private final BusinessHoursService hours;
    private final CalendarGateway calendar;
    private final TenantService tenants;
    private final Clock clock;

    SlotFinder(BookingCatalogRepository catalog, BookingRepository bookings, BusinessHoursService hours,
            CalendarGateway calendar, TenantService tenants, Clock clock) {
        this.catalog = catalog;
        this.bookings = bookings;
        this.hours = hours;
        this.calendar = calendar;
        this.tenants = tenants;
        this.clock = clock;
    }

    /**
     * The nearest free slots, at most {@code limit}. When nothing is free in the next 7 days this naturally
     * returns the first available day's slots (BK-03).
     *
     * @param ignoreBookingId a booking being moved, which doesn't block its own time
     * @param day             only this day (staff slot picker), or null for the nearest
     */
    List<Slot> find(TenantId tenantId, BookableService service, int limit, UUID ignoreBookingId, LocalDate day,
            boolean freshCalendar) {
        Instant now = clock.instant();
        BookingSettings settings = catalog.findSettings(tenantId).orElse(BookingSettings.DEFAULTS);
        ZoneId zone = ZoneId.of(tenants.get(tenantId).timezone());
        Instant earliest = now.plus(Duration.ofMinutes(settings.minNoticeMin()));
        Instant latest = now.plus(Duration.ofDays(settings.maxAdvanceDays()));
        LocalDate firstDay = day != null ? day : earliest.atZone(zone).toLocalDate();
        LocalDate lastDay = day != null ? day : latest.atZone(zone).toLocalDate();
        if (lastDay.isBefore(firstDay)) {
            return List.of();
        }
        Instant windowStart = firstDay.atStartOfDay(zone).toInstant();
        Instant windowEnd = lastDay.plusDays(1).atStartOfDay(zone).toInstant();
        Context ctx = context(tenantId, service, windowStart, windowEnd, ignoreBookingId, freshCalendar);
        List<DayHours> week = hours.week(tenantId);
        Set<LocalDate> closed = new HashSet<>();
        catalog.findHolidays(tenantId, firstDay).stream().filter(Holiday::closed).forEach(h -> closed.add(h.date()));

        List<Slot> slots = new ArrayList<>();
        for (LocalDate date = firstDay; !date.isAfter(lastDay) && slots.size() < limit; date = date.plusDays(1)) {
            if (closed.contains(date)) {
                continue;
            }
            DayHours dayHours = week.get(date.getDayOfWeek().getValue() - 1);
            if (dayHours.opensAt() == null) {
                continue;
            }
            ZonedDateTime start = date.atTime(dayHours.opensAt()).atZone(zone);
            ZonedDateTime close = date.atTime(dayHours.closesAt()).atZone(zone);
            for (; !start.plusMinutes(service.durationMin()).isAfter(close) && slots.size() < limit;
                    start = start.plusMinutes(settings.slotStepMin())) {
                Instant s = start.toInstant();
                if (s.isBefore(earliest) || s.isAfter(latest)) {
                    continue;
                }
                Slot slot = ctx.firstFree(s);
                if (slot != null) {
                    slots.add(slot);
                }
            }
        }
        return slots;
    }

    /**
     * Whether a specific time can be booked now for the service, optionally with a given resource: the same
     * rules as {@link #find}, with fresh calendar data (BK-05). Used when the customer taps a slot.
     */
    Slot check(TenantId tenantId, BookableService service, UUID resourceId, Instant start, UUID ignoreBookingId) {
        Instant now = clock.instant();
        BookingSettings settings = catalog.findSettings(tenantId).orElse(BookingSettings.DEFAULTS);
        ZoneId zone = ZoneId.of(tenants.get(tenantId).timezone());
        if (start.isBefore(now.plus(Duration.ofMinutes(settings.minNoticeMin())))
                || start.isAfter(now.plus(Duration.ofDays(settings.maxAdvanceDays())))) {
            return null;
        }
        ZonedDateTime local = start.atZone(zone);
        LocalDate date = local.toLocalDate();
        if (catalog.holidayExists(tenantId, date)) {
            return null;
        }
        DayHours dayHours = hours.week(tenantId).get(date.getDayOfWeek().getValue() - 1);
        LocalTime end = local.toLocalTime().plusMinutes(service.durationMin());
        if (dayHours.opensAt() == null || local.toLocalTime().isBefore(dayHours.opensAt())
                || end.isAfter(dayHours.closesAt()) || end.isBefore(local.toLocalTime())) {
            return null;
        }
        Instant dayStart = date.atStartOfDay(zone).toInstant();
        Context ctx = context(tenantId, service, dayStart, dayStart.plus(Duration.ofDays(1)), ignoreBookingId, true);
        if (resourceId == null) {
            return ctx.firstFree(start);
        }
        return ctx.free(start, resourceId) ? ctx.slot(start, resourceId) : null;
    }

    private Context context(TenantId tenantId, BookableService service, Instant from, Instant to,
            UUID ignoreBookingId, boolean freshCalendar) {
        List<BookingResource> resources = new ArrayList<>();
        if (!service.resourceIds().isEmpty()) {
            Map<UUID, BookingResource> active = new HashMap<>();
            catalog.findResources(tenantId, true).forEach(r -> active.put(r.id(), r));
            service.resourceIds().stream().map(active::get).filter(r -> r != null).forEach(resources::add);
        }
        Set<String> resourceCalendars = new LinkedHashSet<>();
        resources.stream().filter(r -> r.calendarId() != null && !r.calendarId().isBlank())
                .forEach(r -> resourceCalendars.add(r.calendarId()));
        Map<String, List<CalendarApi.Busy>> busy = calendar.busy(tenantId, resourceCalendars, from, to, freshCalendar);
        List<Booking> taken = bookings.findBlocking(tenantId, from.minus(Duration.ofHours(12)), to, clock.instant())
                .stream().filter(b -> !b.id().equals(ignoreBookingId)).toList();
        return new Context(service, resources, busy, taken);
    }

    /** Everything needed to test start times within one search. */
    private record Context(BookableService service, List<BookingResource> resources,
            Map<String, List<CalendarApi.Busy>> busy, List<Booking> taken) {

        Slot firstFree(Instant start) {
            if (resources.isEmpty()) {
                return free(start, null) ? slot(start, null) : null;
            }
            for (BookingResource r : resources) {
                if (free(start, r.id())) {
                    return slot(start, r.id());
                }
            }
            return null;
        }

        Slot slot(Instant start, UUID resourceId) {
            String name = resources.stream().filter(r -> r.id().equals(resourceId)).map(BookingResource::name)
                    .findFirst().orElse(null);
            return new Slot(service.id(), resourceId, name, start, start.plus(Duration.ofMinutes(service.durationMin())));
        }

        boolean free(Instant start, UUID resourceId) {
            Instant end = start.plus(Duration.ofMinutes(service.durationMin()));
            Instant blockedUntil = end.plus(Duration.ofMinutes(service.bufferMin()));
            if (resourceId != null && resources.stream().noneMatch(r -> r.id().equals(resourceId))) {
                return false;
            }
            for (CalendarApi.Busy b : busy.getOrDefault(CalendarGateway.MAIN, List.of())) {
                if (overlaps(start, blockedUntil, b.start(), b.end())) {
                    return false;
                }
            }
            if (resourceId != null) {
                String own = resources.stream().filter(r -> r.id().equals(resourceId)).map(BookingResource::calendarId)
                        .filter(c -> c != null && !c.isBlank()).findFirst().orElse(null);
                if (own != null) {
                    for (CalendarApi.Busy b : busy.getOrDefault(own, List.of())) {
                        if (overlaps(start, blockedUntil, b.start(), b.end())) {
                            return false;
                        }
                    }
                }
            }
            for (Booking b : taken) {
                // A booking blocks its own resource; bookings without a resource block everyone, and a service
                // without resources is blocked by every booking (one thing at a time).
                boolean sameLane = resourceId == null || b.resourceId() == null || resourceId.equals(b.resourceId());
                if (sameLane && overlaps(start, blockedUntil, b.startsAt(), b.endsAt())) {
                    return false;
                }
            }
            return true;
        }

        private static boolean overlaps(Instant aStart, Instant aEnd, Instant bStart, Instant bEnd) {
            return aStart.isBefore(bEnd) && bStart.isBefore(aEnd);
        }
    }
}
