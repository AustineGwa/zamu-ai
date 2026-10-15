package ai.zamu.privacy;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import ai.zamu.bookings.BookingService;
import ai.zamu.bookings.BookingSettings;
import ai.zamu.bookings.BookingSetupService;
import ai.zamu.bookings.BusinessHoursService;
import ai.zamu.bookings.DayHours;
import ai.zamu.bookings.ServiceForm;
import ai.zamu.shared.ids.TenantId;
import ai.zamu.shared.security.ZamuPrincipal;
import ai.zamu.shared.security.ZamuRole;
import ai.zamu.support.AbstractMySqlIT;
import ai.zamu.support.Seed;
import ai.zamu.support.TenantFixture;
import ai.zamu.tenants.ChangeRequestService;
import ai.zamu.tenants.ChangeRequestType;
import ai.zamu.tenants.TenantService;
import ai.zamu.tenants.TenantStatus;
import ai.zamu.users.UserService;

import static ai.zamu.support.TenantFixture.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Data subject requests (NFR-12, acceptance scenario 16) and retention (NFR-11) on MySQL. Payment records are
 * kept when a customer is deleted; there are none until Milestone 6, so the export's payments list is empty.
 */
@AutoConfigureMockMvc
class PrivacyIT extends AbstractMySqlIT {

    static final String PHONE = "254712345678";

    @Autowired
    MockMvc mvc;

    @Autowired
    TenantService tenants;

    @Autowired
    UserService users;

    @Autowired
    PrivacyService privacy;

    @Autowired
    BusinessHoursService hours;

    @Autowired
    BookingSetupService setup;

    @Autowired
    BookingService bookings;

    @Autowired
    ChangeRequestService changeRequests;

    @Autowired
    ObjectMapper objectMapper;

    TenantFixture fx;
    TenantId a;
    ZamuPrincipal operator;
    UUID customer;
    UUID conversation;

    @BeforeEach
    void setUp() {
        fx = TenantFixture.create(tenants, users);
        a = fx.idA();
        operator = new ZamuPrincipal(UUID.randomUUID(), null, ZamuRole.OPERATOR, "ops@zamu.test", "Ops");
        Instant now = clock.instant();
        customer = Seed.customer(jdbc, a, PHONE, "Wanjiku", now);
        conversation = Seed.conversation(jdbc, a, customer, now, now);
        Seed.message(jdbc, a, conversation, "in", "customer", "I want to book a cleaning", "received", null, now);
        Seed.message(jdbc, a, conversation, "out", "bot", "Which day suits you?", "sent", null, now.plusSeconds(2));
        Seed.handover(jdbc, a, conversation, "requested", now.plusSeconds(3), null);
        hours.saveWeek(a, java.util.Arrays.stream(java.time.DayOfWeek.values())
                .map(d -> new DayHours(d, java.time.LocalTime.of(8, 0), java.time.LocalTime.of(18, 0),
                        java.time.LocalTime.of(8, 0), java.time.LocalTime.of(18, 0)))
                .toList());
        setup.saveSettings(a, new BookingSettings(120, 30, 60, false, 30));
        UUID cleaning = setup.createService(a, new ServiceForm("Cleaning", null, null, 30, null, null, BigDecimal.ZERO,
                0, true, List.of()));
        bookings.hold(a, new BookingService.HoldRequest(customer, "Wanjiku", PHONE, conversation, cleaning, null,
                Instant.parse("2026-10-15T08:00:00Z"), "whatsapp", null));
        // Someone else at the same business, and the same number at another business, are not touched.
        UUID other = Seed.customer(jdbc, a, "254700000002", "Otieno", now);
        Seed.message(jdbc, a, Seed.conversation(jdbc, a, other, now, now), "in", "customer", "Hello", "received", null, now);
        Seed.message(jdbc, fx.idB(), Seed.conversation(jdbc, fx.idB(), Seed.customer(jdbc, fx.idB(), PHONE, "W", now),
                now, now), "in", "customer", "Hi Baraka", "received", null, now);
    }

    private int count(String sql, Object... params) {
        return jdbc.sql(sql).params(params).query(Integer.class).single();
    }

    @Test
    void NFR_12_scenario16_exportHoldsEverythingAboutTheCustomerAndIsAudited() throws Exception {
        assertThat(privacy.findCustomer(a, PHONE)).hasValueSatisfying(c -> assertThat(c.id()).isEqualTo(customer));

        byte[] body = mvc.perform(get("/admin/tenants/{t}/privacy/customers/{c}/export", fx.tenantA().id(), customer)
                        .with(as(operator)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        JsonNode json = objectMapper.readTree(new String(body, StandardCharsets.UTF_8));

        assertThat(json.path("business").asString()).isEqualTo("Afya Clinic");
        assertThat(json.toString()).contains(PHONE, "Wanjiku", "I want to book a cleaning", "Which day suits you?")
                .doesNotContain("Hello", "Hi Baraka");
        assertThat(json.path("handovers")).hasSize(1);
        assertThat(json.path("bookings")).hasSize(1);
        assertThat(json.path("payments").isArray()).isTrue();
        assertThat(count("SELECT COUNT(*) FROM audit_logs WHERE action = 'customer.exported'")).isEqualTo(1);
    }

    @Test
    void NFR_12_scenario16_deleteRemovesTheCustomerAndTheirDataButNothingElse() throws Exception {
        mvc.perform(post("/admin/tenants/{t}/privacy/customers/{c}/delete", fx.tenantA().id(), customer).with(csrf())
                        .with(as(operator)).param("confirmPhone", "0712 345 678"))
                .andExpect(status().is3xxRedirection());

        assertThat(privacy.findCustomer(a, PHONE)).isEmpty();
        assertThat(count("SELECT COUNT(*) FROM conversations WHERE tenant_id = ?", a.bytes())).isEqualTo(1); // Otieno's
        assertThat(count("SELECT COUNT(*) FROM messages WHERE tenant_id = ? AND body LIKE '%cleaning%'", a.bytes())).isZero();
        assertThat(count("SELECT COUNT(*) FROM handovers WHERE tenant_id = ?", a.bytes())).isZero();
        assertThat(count("SELECT COUNT(*) FROM bookings WHERE tenant_id = ?", a.bytes())).isZero();
        assertThat(count("SELECT COUNT(*) FROM customers WHERE phone = ?", PHONE)).isEqualTo(1); // tenant B's
        // The audit trail stays, with the customer's ID only.
        assertThat(count("SELECT COUNT(*) FROM audit_logs WHERE action = 'customer.deleted' AND after_state IS NULL")).isEqualTo(1);
    }

    @Test
    void NFR_12_aMistypedPhoneDeletesNothing() throws Exception {
        mvc.perform(post("/admin/tenants/{t}/privacy/customers/{c}/delete", fx.tenantA().id(), customer).with(csrf())
                        .with(as(operator)).param("confirmPhone", "0700 000 002"))
                .andExpect(status().is3xxRedirection());
        assertThat(privacy.findCustomer(a, PHONE)).isPresent();
        assertThat(count("SELECT COUNT(*) FROM bookings WHERE tenant_id = ?", a.bytes())).isEqualTo(1);
    }

    @Test
    void NFR_09_aCustomerOfAnotherBusinessCannotBeExportedThroughThisOne() throws Exception {
        mvc.perform(get("/admin/tenants/{t}/privacy/customers/{c}/export", fx.tenantB().id(), customer).with(as(operator)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/admin/tenants/{t}/privacy", fx.tenantA().id()).with(as(fx.ownerA(), fx.tenantA())))
                .andExpect(status().isForbidden());
    }

    @Test
    void NFR_11_conversationsInactiveLongerThanTheRetentionPeriodAreDeleted() {
        privacy.saveSettings(a, new RetentionSettings(6, 30));
        Instant old = clock.instant().minus(Duration.ofDays(200));
        UUID stale = Seed.conversation(jdbc, a, customer, old, old);
        Seed.message(jdbc, a, stale, "in", "customer", "Old question", "received", null, old);
        Seed.handover(jdbc, a, stale, "low_confidence", old, null);
        Seed.job(jdbc, "whatsapp.send", "done", 1, old, old);
        Seed.job(jdbc, "whatsapp.send", "done", 1, clock.instant(), clock.instant());

        assertThat(privacy.runRetention()).isEqualTo(1);

        assertThat(count("SELECT COUNT(*) FROM messages WHERE body = 'Old question'")).isZero();
        assertThat(count("SELECT COUNT(*) FROM handovers WHERE tenant_id = ? AND reason = 'low_confidence'", a.bytes())).isZero();
        assertThat(count("SELECT COUNT(*) FROM conversations WHERE tenant_id = ?", a.bytes())).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM jobs WHERE status = 'done'")).isEqualTo(1);
        assertThat(privacy.runRetention()).isZero();
    }

    @Test
    void NFR_11_retentionSettingsAreRangeChecked() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> privacy.saveSettings(a, new RetentionSettings(0, 30)))
                .isInstanceOf(IllegalArgumentException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> privacy.saveSettings(a, new RetentionSettings(12, 400)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(privacy.settings(fx.idB())).isEqualTo(RetentionSettings.DEFAULTS);
    }

    @Test
    void NFR_11_anOffboardedBusinessIsPurgedAfterItsGracePeriodKeepingUsageAndAudit() throws Exception {
        changeRequests.submit(a, fx.ownerA().id(), ChangeRequestType.OTHER, "Please call");
        Seed.usage(jdbc, a, LocalDate.of(2026, 10, 1), 3, BigDecimal.ONE, 0);
        tenants.pause(a);
        mvc.perform(post("/admin/tenants/{t}/privacy/offboard", fx.tenantA().id()).with(csrf()).with(as(operator))
                        .param("confirmName", "afya clinic"))
                .andExpect(status().is3xxRedirection());
        assertThat(tenants.get(a).status()).isEqualTo(TenantStatus.OFFBOARDED);

        clock.advance(Duration.ofDays(29));
        privacy.runRetention();
        assertThat(count("SELECT COUNT(*) FROM conversations WHERE tenant_id = ?", a.bytes())).isEqualTo(2);

        clock.advance(Duration.ofDays(2));
        assertThat(privacy.runRetention()).isEqualTo(2);

        for (String table : new String[] {"conversations", "messages", "customers", "handovers", "bookings", "services",
                "change_requests"}) {
            assertThat(count("SELECT COUNT(*) FROM " + table + " WHERE tenant_id = ?", a.bytes())).as(table).isZero();
        }
        assertThat(count("SELECT COUNT(*) FROM usage_daily WHERE tenant_id = ?", a.bytes())).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM audit_logs WHERE tenant_id = ? AND action = 'tenant.purged'", a.bytes()))
                .isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM conversations WHERE tenant_id = ?", fx.idB().bytes())).isEqualTo(1);
    }

    @Test
    void NFR_11_offboardingNeedsTheNameTypedAndAPausedBusiness() throws Exception {
        mvc.perform(post("/admin/tenants/{t}/privacy/offboard", fx.tenantA().id()).with(csrf()).with(as(operator))
                .param("confirmName", "Afya Clinic"));
        assertThat(tenants.get(a).status()).isNotEqualTo(TenantStatus.OFFBOARDED); // not paused yet
        tenants.pause(a);
        mvc.perform(post("/admin/tenants/{t}/privacy/offboard", fx.tenantA().id()).with(csrf()).with(as(operator))
                .param("confirmName", "Afya"));
        assertThat(tenants.get(a).status()).isEqualTo(TenantStatus.PAUSED);
    }
}
