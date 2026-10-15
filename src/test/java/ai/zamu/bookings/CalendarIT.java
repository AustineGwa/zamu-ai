package ai.zamu.bookings;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import ai.zamu.shared.ids.Uuids;
import ai.zamu.support.AbstractMySqlIT;
import ai.zamu.support.TenantFixture;
import ai.zamu.tenants.TenantService;
import ai.zamu.users.UserService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Google Calendar connection and use, with Google's API replaced (spec: BK-01, BK-02, BK-05, BK-07, BK-10). */
class CalendarIT extends AbstractMySqlIT {

    @MockitoBean
    CalendarApi api;

    @Autowired
    TenantService tenants;

    @Autowired
    UserService users;

    @Autowired
    BusinessHoursService hours;

    @Autowired
    BookingSetupService setup;

    @Autowired
    BookingService bookings;

    @Autowired
    CalendarGateway gateway;

    TenantFixture fx;
    UUID consultation;

    @BeforeEach
    void setUp() {
        fx = TenantFixture.create(tenants, users);
        List<DayHours> week = new ArrayList<>();
        for (DayOfWeek d : DayOfWeek.values()) {
            week.add(new DayHours(d, LocalTime.of(8, 0), LocalTime.of(18, 0), null, null));
        }
        hours.saveWeek(fx.idA(), week);
        consultation = setup.createService(fx.idA(), new ServiceForm("Consultation", null, null, 30, null, null,
                BigDecimal.ZERO, 0, true, List.of()));
        when(api.exchangeCode(anyString(), anyString()))
                .thenReturn(new CalendarApi.Tokens("access-1", 3600, "refresh-secret"));
        when(api.refresh(anyString())).thenReturn(new CalendarApi.Tokens("access-1", 3600, null));
        when(api.freeBusy(anyString(), anyList(), any(), any())).thenReturn(Map.of("primary", List.of()));
        when(api.insertEvent(anyString(), anyString(), any())).thenReturn("event-1");
        gateway.forget(fx.idA());
    }

    @Test
    void BK_01_theRefreshTokenIsStoredEncrypted() {
        setup.completeCalendarConnection(fx.idA(), "code", "https://zamu.test/admin/calendar/callback");

        byte[] stored = jdbc.sql("SELECT oauth_refresh_token FROM calendar_connections WHERE tenant_id = ?")
                .param(fx.idA().bytes()).query(byte[].class).single();
        assertThat(new String(stored, java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotContain("refresh-secret");
        assertThat(setup.calendarStatus(fx.idA()).connected()).isTrue();
    }

    @Test
    void BK_02_busyTimeInTheCalendarIsNotOffered() {
        setup.completeCalendarConnection(fx.idA(), "code", "uri");
        when(api.freeBusy(anyString(), anyList(), any(), any())).thenReturn(Map.of("primary", List.of(
                new CalendarApi.Busy(Instant.parse("2026-10-14T08:00:00Z"), Instant.parse("2026-10-14T09:00:00Z")))));

        List<Slot> slots = bookings.slots(fx.idA(), consultation, 1, null);

        assertThat(slots.get(0).startsAt()).isEqualTo(Instant.parse("2026-10-14T09:00:00Z"));
    }

    @Test
    void BK_05_BK_07_aBookingIsCheckedAgainstFreshFreeBusyAndWrittenToTheCalendar() {
        setup.completeCalendarConnection(fx.idA(), "code", "uri");
        bookings.slots(fx.idA(), consultation, 5, null); // fills the 60-second cache

        Booking b = bookings.hold(fx.idA(), new BookingService.HoldRequest(UUID.randomUUID(), "Wanjiku",
                "254712345678", null, consultation, null, Instant.parse("2026-10-14T08:00:00Z"), "bot", null));

        verify(api, times(2)).freeBusy(anyString(), anyList(), any(), any()); // the hold didn't use the cache
        verify(api).insertEvent(eq("access-1"), eq("primary"), any());
        assertThat(bookings.get(fx.idA(), b.id()).calendarEventId()).isEqualTo("event-1");
    }

    @Test
    void BK_10_anEventDeletedInTheCalendarCancelsTheBooking() {
        setup.completeCalendarConnection(fx.idA(), "code", "uri");
        Booking b = bookings.hold(fx.idA(), new BookingService.HoldRequest(UUID.randomUUID(), "Wanjiku",
                "254712345678", null, consultation, null, Instant.parse("2026-10-15T08:00:00Z"), "bot", null));
        when(api.getEvent(anyString(), anyString(), eq("event-1"))).thenReturn(Optional.empty());

        assertThat(bookings.syncCalendars()).isEqualTo(1);
        assertThat(bookings.get(fx.idA(), b.id()).status()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    void BK_10_anEventMovedInTheCalendarMovesTheBooking() {
        setup.completeCalendarConnection(fx.idA(), "code", "uri");
        Booking b = bookings.hold(fx.idA(), new BookingService.HoldRequest(UUID.randomUUID(), "Wanjiku",
                "254712345678", null, consultation, null, Instant.parse("2026-10-15T08:00:00Z"), "bot", null));
        when(api.getEvent(anyString(), anyString(), eq("event-1"))).thenReturn(Optional.of(new CalendarApi.EventState(
                false, Instant.parse("2026-10-15T10:00:00Z"), Instant.parse("2026-10-15T10:30:00Z"))));

        bookings.syncCalendars();

        assertThat(bookings.get(fx.idA(), b.id()).startsAt()).isEqualTo(Instant.parse("2026-10-15T10:00:00Z"));
    }

    @Test
    void BK_01_revokedAccessStopsBookingAndAlertsOperatorsOnce() {
        jdbc.sql("""
                INSERT INTO users (id, tenant_id, email, name, role, created_at, updated_at)
                VALUES (?, NULL, 'ops@zamu.test', 'Ops', 'operator', NOW(3), NOW(3))
                """).param(Uuids.toBytes(UUID.randomUUID())).update();
        setup.completeCalendarConnection(fx.idA(), "code", "uri");
        gateway.forget(fx.idA());
        when(api.refresh(anyString())).thenThrow(new CalendarApiException("invalid_grant", true));

        assertThatThrownBy(() -> bookings.slots(fx.idA(), consultation, 5, null))
                .isInstanceOf(CalendarUnavailableException.class);
        assertThatThrownBy(() -> bookings.slots(fx.idA(), consultation, 5, null))
                .isInstanceOf(CalendarUnavailableException.class);

        assertThat(setup.calendarStatus(fx.idA()).revoked()).isTrue();
        assertThat(emails.sentTo("ops@zamu.test")).hasSize(1);
    }
}
