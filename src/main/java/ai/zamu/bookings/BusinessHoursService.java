package ai.zamu.bookings;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.time.format.TextStyle;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ai.zamu.audit.AuditEvent;
import ai.zamu.audit.AuditService;
import ai.zamu.shared.ids.TenantId;
import ai.zamu.tenants.TenantService;

/**
 * Opening and handover hours (spec: business_hours; HO-02 after-hours handover message; HO-06 escalation only
 * during handover hours). Times are in the tenant's timezone (NFR-18).
 */
@Service
public class BusinessHoursService {

    private final BusinessHoursRepository repository;
    private final TenantService tenants;
    private final AuditService audit;
    private final Clock clock;

    BusinessHoursService(BusinessHoursRepository repository, TenantService tenants, AuditService audit, Clock clock) {
        this.repository = repository;
        this.tenants = tenants;
        this.audit = audit;
        this.clock = clock;
    }

    /** Monday to Sunday; days without a row are closed. */
    @Transactional(readOnly = true)
    public List<DayHours> week(TenantId tenantId) {
        Map<DayOfWeek, DayHours> byDay = new EnumMap<>(DayOfWeek.class);
        repository.findWeek(tenantId).forEach(d -> byDay.put(d.day(), d));
        List<DayHours> week = new ArrayList<>();
        for (DayOfWeek day : DayOfWeek.values()) {
            week.add(byDay.getOrDefault(day, DayHours.closed(day)));
        }
        return week;
    }

    @Transactional(readOnly = true)
    public boolean configured(TenantId tenantId) {
        return !repository.findWeek(tenantId).isEmpty();
    }

    @Transactional
    public void saveWeek(TenantId tenantId, List<DayHours> week) {
        for (DayHours day : week) {
            check(day.opensAt() == null, day.closesAt() == null, day.day(), "opening");
            check(day.handoverOpensAt() == null, day.handoverClosesAt() == null, day.day(), "handover");
            if (day.opensAt() != null && !day.closesAt().isAfter(day.opensAt())) {
                throw new InvalidHoursException(label(day.day()) + ": closing time must be after opening time");
            }
            if (day.handoverOpensAt() != null && !day.handoverClosesAt().isAfter(day.handoverOpensAt())) {
                throw new InvalidHoursException(label(day.day()) + ": handover end must be after handover start");
            }
        }
        repository.replaceWeek(tenantId, week, clock.instant());
        audit.record(AuditEvent.of(tenantId, "hours.updated", "tenant", tenantId.value()));
    }

    /**
     * Whether staff take handovers right now. ASSUMPTION: a tenant with no hours configured is treated as always
     * open, so handovers are never wrongly reported as after-hours during onboarding.
     */
    @Transactional(readOnly = true)
    public boolean handoverOpen(TenantId tenantId, Instant at) {
        List<DayHours> rows = repository.findWeek(tenantId);
        if (rows.isEmpty()) {
            return true;
        }
        ZonedDateTime local = at.atZone(zone(tenantId));
        return rows.stream().anyMatch(d -> d.day() == local.getDayOfWeek() && d.handoverOpenAt(local.toLocalTime()));
    }

    /** The next time handover hours start, within the coming week, for the after-hours message. */
    @Transactional(readOnly = true)
    public Optional<ZonedDateTime> nextHandoverOpening(TenantId tenantId, Instant after) {
        List<DayHours> week = week(tenantId);
        ZonedDateTime local = after.atZone(zone(tenantId));
        for (int offset = 0; offset <= 7; offset++) {
            LocalDate date = local.toLocalDate().plusDays(offset);
            DayHours day = week.get(date.getDayOfWeek().getValue() - 1);
            if (day.handoverOpensAt() == null) {
                continue;
            }
            ZonedDateTime opening = date.atTime(day.handoverOpensAt()).atZone(local.getZone());
            if (opening.isAfter(local)) {
                return Optional.of(opening);
            }
        }
        return Optional.empty();
    }

    private ZoneId zone(TenantId tenantId) {
        return ZoneId.of(tenants.get(tenantId).timezone());
    }

    private static void check(boolean startEmpty, boolean endEmpty, DayOfWeek day, String what) {
        if (startEmpty != endEmpty) {
            throw new InvalidHoursException(label(day) + ": fill in both " + what + " times, or neither");
        }
    }

    public static String label(DayOfWeek day) {
        return day.getDisplayName(TextStyle.FULL, Locale.ENGLISH);
    }
}
