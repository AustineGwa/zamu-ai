package ai.zamu.reports;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.web.servlet.MockMvc;

import ai.zamu.conversations.ConversationEvents;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Usage, running cost and margin per client (REP-03, REP-04, REP-05) and the running-cost alert (NFR-19). Rates
 * are the defaults in application.yml: USD 1 = KES 129, USD 0.01 per WhatsApp template message, alert at 40%.
 */
@AutoConfigureMockMvc
class UsageReportIT extends AbstractMySqlIT {

    static final YearMonth OCTOBER = YearMonth.of(2026, 10);

    @Autowired
    MockMvc mvc;

    @Autowired
    TenantService tenants;

    @Autowired
    UserService users;

    @Autowired
    UsageReportService usage;

    @Autowired
    ApplicationEventPublisher events;

    TenantFixture fx;

    @BeforeEach
    void setUp() {
        fx = TenantFixture.create(tenants, users);
    }

    private UsageReportService.UsageRow row(String tenant) {
        return usage.month(OCTOBER).stream().filter(r -> r.tenant().equals(tenant)).findFirst().orElseThrow();
    }

    @Test
    void REP_03_templateMessagesAreCountedAsWhatsAppBillable() {
        events.publishEvent(new ConversationEvents.TemplateQueued(fx.idA(), clock.instant()));
        events.publishEvent(new ConversationEvents.TemplateQueued(fx.idA(), clock.instant()));

        assertThat(row("Afya Clinic").waBillableMessages()).isEqualTo(2);
        assertThat(row("Baraka Salon").waBillableMessages()).isZero();
    }

    @Test
    void REP_03_REP_04_runningCostOverageAndShareOfThePlanFee() {
        int limit = fx.tenantA().conversationLimit();
        Seed.usage(jdbc, fx.idA(), LocalDate.of(2026, 10, 1), limit, new BigDecimal("2.000000"), 100);
        Seed.usage(jdbc, fx.idA(), LocalDate.of(2026, 10, 2), 5, new BigDecimal("1.000000"), 0);
        Seed.usage(jdbc, fx.idA(), LocalDate.of(2026, 9, 30), 999, new BigDecimal("50"), 0); // September: not counted

        UsageReportService.UsageRow a = row("Afya Clinic");

        assertThat(a.conversations()).isEqualTo(limit + 5);
        assertThat(a.overage()).isEqualTo(5);
        assertThat(a.aiCostUsd()).isEqualByComparingTo("3.0000");
        assertThat(a.waBillableMessages()).isEqualTo(100);
        // (3.00 + 100 x 0.01) x 129 = 516.00
        assertThat(a.runningCostKes()).isEqualByComparingTo("516.00");
        assertThat(a.planFeeKes()).isPositive();
        assertThat(a.costSharePercent()).isEqualTo(new BigDecimal("51600")
                .divide(a.planFeeKes(), 0, java.math.RoundingMode.HALF_UP).intValue());
        assertThat(row("Baraka Salon").runningCostKes()).isEqualByComparingTo("0");
    }

    @Test
    void NFR_19_theTeamIsAlertedOnceAMonthWhenRunningCostPassesTheAlertShare() {
        Seed.platformUser(jdbc, "ops@zamu.test", "operator");
        Seed.usage(jdbc, fx.idA(), LocalDate.of(2026, 10, 1), 10, new BigDecimal("1000"), 0); // KES 129,000
        assertThat(row("Afya Clinic").flagged()).isTrue();
        assertThat(row("Baraka Salon").flagged()).isFalse();

        assertThat(usage.sendCostAlerts()).isEqualTo(1);
        assertThat(emails.sentTo("ops@zamu.test")).singleElement()
                .satisfies(m -> assertThat(m.subject()).isEqualTo("Running cost above 40% of plan fee: Afya Clinic"));

        assertThat(usage.sendCostAlerts()).isZero();
        assertThat(emails.sentTo("ops@zamu.test")).hasSize(1);
    }

    @Test
    void REP_05_adminsExportUsageAsCsvForInvoicingAndTheExportIsAudited() throws Exception {
        ZamuPrincipal admin = new ZamuPrincipal(java.util.UUID.randomUUID(), null, ZamuRole.ADMIN, "a@zamu.test", "Admin");
        Seed.usage(jdbc, fx.idA(), LocalDate.of(2026, 10, 1), 12, BigDecimal.ZERO, 0);

        String csv = mvc.perform(get("/admin/usage/export.csv").param("month", "2026-10").with(as(admin)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        List<String> lines = csv.lines().toList();
        assertThat(lines.getFirst()).startsWith("month,tenant,plan,status,billable_conversations,");
        assertThat(lines).anySatisfy(l -> assertThat(l).startsWith("2026-10,Afya Clinic,").contains(",12,"));
        assertThat(jdbc.sql("SELECT COUNT(*) FROM audit_logs WHERE action = 'usage.exported'").query(Integer.class).single())
                .isEqualTo(1);
    }
}
