package ai.zamu.reports;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import ai.zamu.bookings.Booking;
import ai.zamu.bookings.BookingService;
import ai.zamu.bookings.BookingSettings;
import ai.zamu.bookings.BookingSetupService;
import ai.zamu.bookings.BookingStatus;
import ai.zamu.bookings.BusinessHoursService;
import ai.zamu.bookings.DayHours;
import ai.zamu.bookings.ServiceForm;
import ai.zamu.shared.ids.TenantId;
import ai.zamu.shared.mail.EmailMessage;
import ai.zamu.shared.security.ZamuPrincipal;
import ai.zamu.shared.security.ZamuRole;
import ai.zamu.support.AbstractMySqlIT;
import ai.zamu.support.Seed;
import ai.zamu.support.TenantFixture;
import ai.zamu.tenants.TenantService;
import ai.zamu.users.UserService;

import static ai.zamu.support.TenantFixture.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The owner's weekly report on MySQL (REP-01, REP-02, REP-05, NOT-03; acceptance scenario 15: the report's numbers
 * match the database). The clock starts on Wednesday 14 October 2026, 09:00 in Nairobi; the report week is Monday
 * 12 to Sunday 18 October. Opening hours are 08:00 to 18:00, Monday to Saturday.
 */
@AutoConfigureMockMvc
class ReportsIT extends AbstractMySqlIT {

    static final Instant MON_19_0800 = Instant.parse("2026-10-19T05:00:00Z");

    @Autowired
    MockMvc mvc;

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
    ReportService reports;

    @Autowired
    WeeklyReportMailer mailer;

    TenantFixture fx;
    TenantId a;

    @BeforeEach
    void setUp() {
        fx = TenantFixture.create(tenants, users);
        a = fx.idA();
        List<DayHours> week = new ArrayList<>();
        for (DayOfWeek d : DayOfWeek.values()) {
            week.add(d == DayOfWeek.SUNDAY ? DayHours.closed(d)
                    : new DayHours(d, LocalTime.of(8, 0), LocalTime.of(18, 0), LocalTime.of(8, 0), LocalTime.of(18, 0)));
        }
        hours.saveWeek(a, week);
        setup.saveSettings(a, new BookingSettings(120, 30, 60, false, 30));
    }

    /** Three conversations this week (one after hours), two handovers, two bookings (one after hours), one no-show. */
    private void seedWeek() {
        Instant wed0900 = Instant.parse("2026-10-14T06:00:00Z");
        Instant wed2030 = Instant.parse("2026-10-14T17:30:00Z");
        Instant tue0800 = Instant.parse("2026-10-13T05:00:00Z");
        UUID wanjiku = Seed.customer(jdbc, a, "254712000001", "Wanjiku", tue0800);
        UUID otieno = Seed.customer(jdbc, a, "254712000002", "Otieno", tue0800);
        UUID c1 = Seed.conversation(jdbc, a, wanjiku, wed0900, wed0900);
        UUID c2 = Seed.conversation(jdbc, a, otieno, wed2030, wed2030);
        Seed.conversation(jdbc, a, Seed.customer(jdbc, a, "254712000003", null, tue0800), tue0800, tue0800);
        // Last week's conversation and tenant B's are not counted.
        Seed.conversation(jdbc, a, wanjiku, Instant.parse("2026-10-09T06:00:00Z"), Instant.parse("2026-10-09T06:00:00Z"));
        Seed.conversation(jdbc, fx.idB(), Seed.customer(jdbc, fx.idB(), "254712000009", null, tue0800), wed0900, wed0900);

        UUID prices = Seed.kbEntry(jdbc, a, "Consultation fee", tue0800);
        UUID parking = Seed.kbEntry(jdbc, a, "Parking", tue0800);
        Seed.message(jdbc, a, c1, "out", "bot", "KES 1,500.", "sent", "[\"" + prices + "\"]", wed0900);
        Seed.message(jdbc, a, c1, "out", "bot", "KES 1,500 and parking.", "sent",
                "[\"" + prices + "\", \"" + parking + "\"]", wed0900.plusSeconds(60));
        Seed.message(jdbc, a, c2, "in", "customer", "Do you take NHIF cards?", "received", null, wed2030);

        Seed.handover(jdbc, a, c1, "requested", wed0900.plusSeconds(300), wed0900.plusSeconds(540));       // 4 minutes
        Seed.handover(jdbc, a, c2, "low_confidence", wed2030.plusSeconds(60), wed2030.plusSeconds(660)); // 10 minutes

        UUID consultation = setup.createService(a, new ServiceForm("Consultation", null, null, 30, null, null,
                BigDecimal.ZERO, 0, true, List.of()));
        Booking first = bookings.hold(a, new BookingService.HoldRequest(null, "Wanjiku", "254712000001", null,
                consultation, null, Instant.parse("2026-10-14T08:00:00Z"), "staff", null)); // made 09:00, in hours
        clock.set(Instant.parse("2026-10-14T17:00:00Z")); // 20:00 Nairobi
        bookings.hold(a, new BookingService.HoldRequest(null, "Otieno", "254712000002", null, consultation, null,
                Instant.parse("2026-10-15T08:00:00Z"), "staff", null)); // made after hours
        bookings.markOutcome(a, first.id(), BookingStatus.NO_SHOW);
    }

    @Test
    void REP_01_REP_02_scenario15_theWeeklyNumbersMatchTheDatabase() {
        seedWeek();

        WeeklyReport r = reports.weekly(a, LocalDate.of(2026, 10, 14));

        assertThat(r.weekStart()).isEqualTo(LocalDate.of(2026, 10, 12));
        assertThat(r.weekEnd()).isEqualTo(LocalDate.of(2026, 10, 18));
        assertThat(r.conversations()).isEqualTo(count("SELECT COUNT(*) FROM conversations WHERE tenant_id = ? "
                + "AND opened_at >= '2026-10-11 21:00:00' AND opened_at < '2026-10-18 21:00:00'")).isEqualTo(3);
        assertThat(r.afterHoursConversations()).isEqualTo(1);
        assertThat(r.handedOver()).isEqualTo(2);
        assertThat(r.handoverRatePercent()).isEqualTo(67);
        assertThat(r.medianFirstReplyMinutes()).isEqualTo(7);
        assertThat(r.bookings()).isEqualTo(count("SELECT COUNT(*) FROM bookings WHERE tenant_id = ?")).isEqualTo(2);
        assertThat(r.afterHoursBookings()).isEqualTo(1);
        assertThat(r.bookingsByService()).containsExactly(new WeeklyReport.ServiceCount("Consultation", 2));
        assertThat(r.noShows()).isEqualTo(1);
        assertThat(r.paymentsCollectedKes()).isEqualByComparingTo("0");
        assertThat(r.topQuestions()).containsExactly(new WeeklyReport.TopQuestion("Consultation fee", 2),
                new WeeklyReport.TopQuestion("Parking", 1));
        assertThat(r.unanswered()).extracting(WeeklyReport.Unanswered::question).containsExactly("Do you take NHIF cards?");
    }

    @Test
    void REP_05_ownersDownloadTheWeekAsCsvAndTheExportIsAudited() throws Exception {
        seedWeek();

        String csv = mvc.perform(get("/app/reports/weekly.csv").param("week", "2026-10-12")
                        .with(as(fx.ownerA(), fx.tenantA())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(csv).startsWith("metric,value\nweek_start,2026-10-12\n")
                .contains("conversations,3\n", "after_hours_conversations,1\n", "no_shows,1\n",
                        "bookings: Consultation,2\n", "unanswered,Do you take NHIF cards?\n");
        assertThat(count("SELECT COUNT(*) FROM audit_logs WHERE tenant_id = ? AND action = 'report.exported'")).isEqualTo(1);
    }

    @Test
    void REP_01_theReportPageShowsOnlyTheirOwnBusiness() throws Exception {
        seedWeek();
        mvc.perform(get("/app/reports").param("week", "2026-10-12").with(as(fx.ownerB(), fx.tenantB())))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("NHIF"))));
        mvc.perform(get("/app/reports").param("week", "2026-10-12").with(as(fx.ownerA(), fx.tenantA())))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Do you take NHIF cards?")));
    }

    @Test
    void REP_01_reportsAreForOwnersNotStaff() throws Exception {
        mvc.perform(get("/app/reports").with(as(fx.staffA(), fx.tenantA()))).andExpect(status().isForbidden());
        mvc.perform(get("/app/reports/weekly.csv").with(as(fx.staffA(), fx.tenantA()))).andExpect(status().isForbidden());
    }

    @Test
    void REP_03_NFR_14_usageAndTheAuditLogAreForZamuAdminsOnly() throws Exception {
        ZamuPrincipal operator = new ZamuPrincipal(UUID.randomUUID(), null, ZamuRole.OPERATOR, "ops@zamu.test", "Ops");
        ZamuPrincipal admin = new ZamuPrincipal(UUID.randomUUID(), null, ZamuRole.ADMIN, "admin@zamu.test", "Admin");
        for (String path : new String[] {"/admin/usage", "/admin/usage/export.csv", "/admin/audit", "/admin/audit/export.csv"}) {
            mvc.perform(get(path).with(as(operator))).andExpect(status().isForbidden());
            mvc.perform(get(path).with(as(fx.ownerA(), fx.tenantA()))).andExpect(status().isForbidden());
            mvc.perform(get(path).with(as(admin))).andExpect(status().isOk());
        }
    }

    @Test
    void REP_01_NOT_03_theWeeklyEmailGoesOutOnMondayFrom8amOnce() {
        seedWeek();
        clock.set(MON_19_0800.minusSeconds(60));
        assertThat(mailer.sendDue()).isZero();

        clock.set(MON_19_0800);
        emails.clear();
        assertThat(mailer.sendDue()).isEqualTo(2); // both fixture tenants have an owner

        List<EmailMessage> toOwnerA = emails.sentTo("owner@afya.test");
        assertThat(toOwnerA).hasSize(1);
        assertThat(toOwnerA.getFirst().subject()).isEqualTo("Your Zamu week: Mon 12 Oct to Sun 18 Oct");
        assertThat(toOwnerA.getFirst().textBody()).contains("Conversations: 3 (1 after hours)", "No-shows: 1",
                "/app/reports?week=2026-10-12");
        assertThat(emails.sentTo("staff@afya.test")).isEmpty();

        clock.set(MON_19_0800.plusSeconds(3600));
        assertThat(mailer.sendDue()).isZero();
        assertThat(emails.sentTo("owner@afya.test")).hasSize(1);
    }

    @Test
    void REP_01_offboardedBusinessesGetNoWeeklyEmail() {
        tenants.pause(fx.idB());
        tenants.offboard(fx.idB());
        clock.set(MON_19_0800);
        emails.clear();
        assertThat(mailer.sendDue()).isEqualTo(1);
        assertThat(emails.sentTo("owner@baraka.test")).isEmpty();
    }

    private int count(String sql) {
        return jdbc.sql(sql).param(a.bytes()).query(Integer.class).single();
    }
}
