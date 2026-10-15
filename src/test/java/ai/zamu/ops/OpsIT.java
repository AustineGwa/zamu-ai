package ai.zamu.ops;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import ai.zamu.shared.ids.Uuids;
import ai.zamu.shared.security.ZamuPrincipal;
import ai.zamu.shared.security.ZamuRole;
import ai.zamu.support.AbstractMySqlIT;
import ai.zamu.support.Seed;
import ai.zamu.support.TenantFixture;
import ai.zamu.tenants.TenantService;
import ai.zamu.users.UserService;

import static ai.zamu.support.TenantFixture.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Monitoring, alerts, dead-job retry and the review queue (NFR-04, NFR-16; admin console Health and Review). */
@AutoConfigureMockMvc
class OpsIT extends AbstractMySqlIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    OpsMonitor monitor;

    @Autowired
    OpsService ops;

    @Autowired
    TenantService tenants;

    @Autowired
    UserService users;

    ZamuPrincipal operator;

    @BeforeEach
    void setUp() {
        monitor.reset();
        Seed.platformUser(jdbc, "ops@zamu.test", "operator");
        operator = new ZamuPrincipal(UUID.randomUUID(), null, ZamuRole.OPERATOR, "ops@zamu.test", "Ops");
    }

    @Test
    void NFR_16_aBackedUpQueueAlertsTheTeamAtMostEvery30Minutes() {
        Instant now = clock.instant();
        Seed.job(jdbc, "whatsapp.send", "queued", 0, now.minus(Duration.ofMinutes(3)), now.minus(Duration.ofMinutes(3)));

        assertThat(monitor.check()).isEqualTo(1);
        assertThat(emails.sentTo("ops@zamu.test")).singleElement()
                .satisfies(m -> assertThat(m.subject()).isEqualTo("Zamu alert: Zamu job queue is backed up"));

        clock.advance(Duration.ofMinutes(10));
        assertThat(monitor.check()).isZero();

        clock.advance(Duration.ofMinutes(21));
        assertThat(monitor.check()).isEqualTo(1);
        assertThat(emails.sentTo("ops@zamu.test")).hasSize(2);
    }

    @Test
    void NFR_16_aQueueThatIsKeepingUpRaisesNoAlert() {
        Instant now = clock.instant();
        Seed.job(jdbc, "whatsapp.send", "queued", 0, now.minusSeconds(30), now.minusSeconds(30));
        Seed.job(jdbc, "whatsapp.send", "queued", 0, now.plusSeconds(600), now); // not due yet

        assertThat(monitor.check()).isZero();
        assertThat(emails.sent()).isEmpty();
    }

    @Test
    void NFR_04_jobsThatDieAreReportedOnceAndCanBeRetriedFromHealth() throws Exception {
        monitor.check(); // baseline
        clock.advance(Duration.ofMinutes(1));
        UUID dead = Seed.job(jdbc, "calendar.sync", "dead", 5, clock.instant().minusSeconds(600), clock.instant());
        clock.advance(Duration.ofSeconds(1));

        assertThat(monitor.check()).isEqualTo(1);
        assertThat(emails.sentTo("ops@zamu.test").getLast().textBody()).startsWith("1 jobs moved to dead");
        clock.advance(Duration.ofMinutes(1));
        assertThat(monitor.check()).isZero(); // the same dead job is not reported again

        mvc.perform(get("/admin/health").with(as(operator)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("calendar.sync")));
        mvc.perform(post("/admin/jobs/{id}/retry", dead).with(csrf()).with(as(operator)))
                .andExpect(status().is3xxRedirection());

        assertThat(jdbc.sql("SELECT status FROM jobs WHERE id = ?").param(Uuids.toBytes(dead)).query(String.class).single())
                .isEqualTo("queued");
        assertThat(jdbc.sql("SELECT attempts FROM jobs WHERE id = ?").param(Uuids.toBytes(dead)).query(Integer.class).single())
                .isZero();
        assertThat(jdbc.sql("SELECT COUNT(*) FROM audit_logs WHERE action = 'job.retried'").query(Integer.class).single())
                .isEqualTo(1);
        // Retrying a job that isn't dead is refused.
        mvc.perform(post("/admin/jobs/{id}/retry", dead).with(csrf()).with(as(operator)))
                .andExpect(status().isNotFound());
    }

    @Test
    void NFR_11_theReviewQueueListsUnsureAnswersAndFailedSendsButNoSensitiveText() {
        TenantFixture fx = TenantFixture.create(tenants, users);
        Instant now = clock.instant();
        UUID customer = Seed.customer(jdbc, fx.idA(), "254712000001", "Wanjiku", now);
        UUID unsure = Seed.conversation(jdbc, fx.idA(), customer, now, now);
        Seed.message(jdbc, fx.idA(), unsure, "in", "customer", "Do you do braces?", "received", null, now);
        Seed.handover(jdbc, fx.idA(), unsure, "low_confidence", now.plusSeconds(5), null);
        UUID sensitive = Seed.conversation(jdbc, fx.idA(), Seed.customer(jdbc, fx.idA(), "254712000002", null, now), now, now);
        Seed.message(jdbc, fx.idA(), sensitive, "in", "customer", "I am bleeding a lot", "received", null, now);
        Seed.handover(jdbc, fx.idA(), sensitive, "sensitive", now.plusSeconds(6), null);
        Seed.message(jdbc, fx.idB(), Seed.conversation(jdbc, fx.idB(),
                Seed.customer(jdbc, fx.idB(), "254712000003", null, now), now, now), "out", "bot", "Hi", "failed", null,
                now.plusSeconds(7));
        Seed.handover(jdbc, fx.idA(), unsure, "requested", now.plusSeconds(8), null); // asked for a person: not listed

        assertThat(ops.review(2)).extracting(OpsRepository.ReviewItem::kind)
                .containsExactly("send_failed", "sensitive", "low_confidence");
        assertThat(ops.review(2)).filteredOn(i -> i.kind().equals("sensitive")).singleElement()
                .satisfies(i -> assertThat(i.detail()).isNull());
        assertThat(ops.review(2)).filteredOn(i -> i.kind().equals("low_confidence")).singleElement()
                .satisfies(i -> assertThat(i.detail()).isEqualTo("Do you do braces?"));
    }

    @Test
    void healthAndReviewAreForTheZamuTeamOnly() throws Exception {
        TenantFixture fx = TenantFixture.create(tenants, users);
        for (String path : new String[] {"/admin/health", "/admin/review"}) {
            mvc.perform(get(path).with(as(fx.ownerA(), fx.tenantA()))).andExpect(status().isForbidden());
            mvc.perform(get(path).with(as(operator))).andExpect(status().isOk());
        }
    }
}
