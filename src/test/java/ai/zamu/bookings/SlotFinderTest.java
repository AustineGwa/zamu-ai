package ai.zamu.bookings;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ai.zamu.shared.ids.TenantId;
import ai.zamu.tenants.TenantLanguage;
import ai.zamu.tenants.TenantPlan;
import ai.zamu.tenants.TenantService;
import ai.zamu.tenants.TenantStatus;
import ai.zamu.tenants.TenantTone;
import ai.zamu.tenants.TenantView;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Free slots without Spring or a database (spec: BK-02, BK-03, BK-04, BK-05). Times are Nairobi (UTC+3). */
class SlotFinderTest {

    static final UUID TENANT = UUID.fromString("0190a000-0000-7000-8000-000000000001");
    static final TenantId T = TenantId.of(TENANT);
    /** Wednesday 14 October 2026, 09:00 in Nairobi. */
    static final Instant NOW = Instant.parse("2026-10-14T06:00:00Z");
    static final UUID OTIENO = UUID.randomUUID();
    static final UUID WANJIRU = UUID.randomUUID();

    BookingCatalogRepository catalog = mock(BookingCatalogRepository.class);
    BookingRepository bookings = mock(BookingRepository.class);
    BusinessHoursService hours = mock(BusinessHoursService.class);
    CalendarGateway calendar = mock(CalendarGateway.class);
    TenantService tenants = mock(TenantService.class);
    SlotFinder finder;
    BookableService consultation = service(30, 0, List.of());

    @BeforeEach
    void setUp() {
        when(tenants.get(any())).thenReturn(tenant());
        when(catalog.findSettings(any())).thenReturn(Optional.of(BookingSettings.DEFAULTS));
        when(catalog.findHolidays(any(), any())).thenReturn(List.of());
        when(catalog.findResources(any(), anyBoolean())).thenReturn(List.of(
                new BookingResource(OTIENO, "Dr Otieno", null, null, true),
                new BookingResource(WANJIRU, "Dr Wanjiru", null, "wanjiru@clinic.example", true)));
        when(hours.week(any())).thenReturn(week());
        when(calendar.busy(any(), any(), any(), any(), anyBoolean())).thenReturn(Map.of());
        when(bookings.findBlocking(any(), any(), any(), any())).thenReturn(List.of());
        finder = new SlotFinder(catalog, bookings, hours, calendar, tenants, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void BK_03_BK_04_theNearestFiveSlotsStartAfterTheMinimumNotice() {
        List<Slot> slots = finder.find(T, consultation, 5, null, null, false);

        assertThat(slots).extracting(s -> s.startsAt().toString()).containsExactly(
                "2026-10-14T08:00:00Z", "2026-10-14T08:30:00Z", "2026-10-14T09:00:00Z", "2026-10-14T09:30:00Z",
                "2026-10-14T10:00:00Z"); // 11:00 to 13:00 Nairobi: two hours' notice from 09:00
    }

    @Test
    void BK_02_closedDaysAndClosingTimeAreRespected() {
        when(catalog.findHolidays(any(), any())).thenReturn(List.of(new Holiday(UUID.randomUUID(),
                LocalDate.of(2026, 10, 15), true, "Closed")));

        List<Slot> slots = finder.find(T, consultation, 64, null, null, false);

        assertThat(slots).noneMatch(s -> s.startsAt().atOffset(ZoneOffset.ofHours(3)).toLocalDate()
                .equals(LocalDate.of(2026, 10, 15)));
        assertThat(slots).noneMatch(s -> s.startsAt().atOffset(ZoneOffset.ofHours(3)).getDayOfWeek() == DayOfWeek.SUNDAY);
        assertThat(slots).allMatch(s -> !s.endsAt().atOffset(ZoneOffset.ofHours(3)).toLocalTime().isAfter(LocalTime.of(18, 0)));
    }

    @Test
    void BK_02_zamuBookingsAndCalendarBusyTimesBlockSlots() {
        when(bookings.findBlocking(any(), any(), any(), any())).thenReturn(List.of(booking(null,
                Instant.parse("2026-10-14T08:00:00Z"), Instant.parse("2026-10-14T09:00:00Z"))));
        when(calendar.busy(any(), any(), any(), any(), anyBoolean())).thenReturn(Map.of(CalendarGateway.MAIN,
                List.of(new CalendarApi.Busy(Instant.parse("2026-10-14T09:30:00Z"), Instant.parse("2026-10-14T10:00:00Z")))));

        List<Slot> slots = finder.find(T, consultation, 3, null, null, false);

        assertThat(slots).extracting(s -> s.startsAt().toString())
                .containsExactly("2026-10-14T09:00:00Z", "2026-10-14T10:00:00Z", "2026-10-14T10:30:00Z");
    }

    @Test
    void BK_02_aSecondDentistTakesTheSameTimeAndOwnCalendarsCount() {
        BookableService checkup = service(45, 15, List.of(OTIENO, WANJIRU));
        Instant eleven = Instant.parse("2026-10-14T08:00:00Z");
        when(bookings.findBlocking(any(), any(), any(), any())).thenReturn(List.of(booking(OTIENO, eleven,
                eleven.plusSeconds(45 * 60))));

        Slot first = finder.find(T, checkup, 1, null, null, false).get(0);
        assertThat(first.startsAt()).isEqualTo(eleven);
        assertThat(first.resourceName()).isEqualTo("Dr Wanjiru");

        when(calendar.busy(any(), any(), any(), any(), anyBoolean())).thenReturn(Map.of("wanjiru@clinic.example",
                List.of(new CalendarApi.Busy(eleven, eleven.plusSeconds(3600)))));
        assertThat(finder.find(T, checkup, 1, null, null, false).get(0).startsAt()).isAfter(eleven);
    }

    @Test
    void BK_05_aTappedTimeIsCheckedAgainAndRejectedOutsideTheRules() {
        assertThat(finder.check(T, consultation, null, Instant.parse("2026-10-14T08:00:00Z"), null)).isNotNull();
        assertThat(finder.check(T, consultation, null, Instant.parse("2026-10-14T06:30:00Z"), null))
                .as("inside the minimum notice").isNull();
        assertThat(finder.check(T, consultation, null, Instant.parse("2026-10-14T15:45:00Z"), null))
                .as("ends after closing").isNull();
        assertThat(finder.check(T, consultation, null, Instant.parse("2026-12-24T08:00:00Z"), null))
                .as("beyond the 30-day advance limit").isNull();
    }

    @Test
    void BK_05_aMovedBookingDoesNotBlockItsOwnTime() {
        Booking own = booking(null, Instant.parse("2026-10-14T08:00:00Z"), Instant.parse("2026-10-14T08:30:00Z"));
        when(bookings.findBlocking(any(), any(), any(), any())).thenReturn(List.of(own));

        assertThat(finder.check(T, consultation, null, own.startsAt(), null)).isNull();
        assertThat(finder.check(T, consultation, null, own.startsAt(), own.id())).isNotNull();
    }

    static BookableService service(int minutes, int buffer, List<UUID> resources) {
        return new BookableService(UUID.randomUUID(), "Service", null, null, minutes, null, null, BigDecimal.ZERO,
                buffer, true, resources);
    }

    static Booking booking(UUID resourceId, Instant start, Instant end) {
        return new Booking(UUID.randomUUID(), TENANT, UUID.randomUUID(), "A", "254712345678", null, UUID.randomUUID(),
                "Other", null, BigDecimal.ZERO, resourceId, null, start, end, BookingStatus.CONFIRMED, null, null, "bot",
                null, null, null, NOW, NOW);
    }

    static List<DayHours> week() {
        List<DayHours> week = new ArrayList<>();
        for (DayOfWeek d : DayOfWeek.values()) {
            week.add(d == DayOfWeek.SUNDAY ? DayHours.closed(d)
                    : new DayHours(d, LocalTime.of(8, 0), LocalTime.of(18, 0), null, null));
        }
        return week;
    }

    static TenantView tenant() {
        return new TenantView(TENANT, "Afya Ltd", "Afya", TenantPlan.GROWTH, TenantStatus.LIVE, "Africa/Nairobi",
                TenantLanguage.EN, TenantTone.FRIENDLY, 1500, new BigDecimal("0.60"), null, null, null, NOW);
    }
}
