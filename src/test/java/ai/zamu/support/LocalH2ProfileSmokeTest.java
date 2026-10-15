package ai.zamu.support;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import ai.zamu.conversations.Channel;
import ai.zamu.conversations.ConversationService;
import ai.zamu.conversations.InboundMessage;
import ai.zamu.conversations.MessageType;
import ai.zamu.shared.ids.TenantId;
import ai.zamu.shared.jobs.JobDrain;
import ai.zamu.shared.security.ZamuPrincipal;
import ai.zamu.shared.security.ZamuRole;
import ai.zamu.tenants.TenantService;
import ai.zamu.tenants.TenantView;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Checks that a plain run with no profile set (like an IDE run) uses the h2 profile and works: H2 migrations,
 * sessions, demo data, login. It is not a substitute for the MySQL integration tests (spec: Testing); it only
 * keeps local development working.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:smoke-${random.uuid};MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "zamu.jobs.enabled=false"})
@AutoConfigureMockMvc
@Import(TestSupportConfig.class)
class LocalH2ProfileSmokeTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    TenantService tenants;

    @Autowired
    RecordingEmailSender emails;

    @Autowired
    Environment environment;

    @Autowired
    JobDrain jobs;

    @Autowired
    ConversationService conversations;

    @Autowired
    JdbcClient jdbc;

    @Test
    void withNoProfileSetTheDefaultIsH2() {
        assertThat(environment.getActiveProfiles()).isEmpty();
        assertThat(environment.getDefaultProfiles()).containsExactly("h2");
    }

    @Test
    void visitorsSeeTheLandingPageAndCanSignUp() throws Exception {
        assertThat(mvc.perform(get("/")).andReturn().getResponse().getContentAsString())
                .contains("Zamu answers every call and every WhatsApp");

        MvcResult signedUp = mvc.perform(post("/signup").with(csrf())
                .param("businessName", "Smoke Test Salon").param("contactName", "Wanjiru")
                .param("phone", "0712 345 678").param("businessType", "SALON").param("consent", "true")).andReturn();
        assertThat(signedUp.getResponse().getRedirectedUrl()).isEqualTo("/signup/thanks");
    }

    @Test
    void thePricingPageShowsTheSeededTiers() throws Exception {
        String page = mvc.perform(get("/pricing")).andReturn().getResponse().getContentAsString();
        assertThat(page).contains("Starter", "3,500", "1-week free trial", "Growth", "20,000", "2-week free trial",
                "Business", "75,000", "Enterprise", "120,000", "1-month free trial", "Compare every feature");
        String signUp = mvc.perform(get("/signup").param("plan", "business")).andReturn().getResponse().getContentAsString();
        assertThat(signUp).contains("data-chosen-plan=\"business\"");
        assertThat(mvc.perform(get("/")).andReturn().getResponse().getContentAsString()).contains("/signup?plan=starter");
    }

    @Test
    void startsWithDemoDataAndOwnerCanLogIn() throws Exception {
        assertThat(tenants.listAll()).extracting(TenantView::displayName).containsExactly("Demo Clinic");

        Browser browser = new Browser(mvc);
        browser.perform(post("/login/code").param("email", "owner@demo.local").with(csrf()));
        String code = emails.latestCodeFor("owner@demo.local").orElseThrow();
        MvcResult verified = browser.perform(post("/login/verify").param("code", code).with(csrf()));
        assertThat(verified.getResponse().getRedirectedUrl()).isEqualTo("/");

        MvcResult staffPage = browser.perform(get("/app/users"));
        assertThat(staffPage.getResponse().getStatus()).isEqualTo(200);
        assertThat(staffPage.getResponse().getContentAsString()).contains("staff@demo.local");
    }

    @Test
    void theWhatsAppSimulatorReachesTheInboxAndConversationPage() throws Exception {
        Browser browser = new Browser(mvc);
        browser.perform(post("/login/code").param("email", "owner@demo.local").with(csrf()));
        browser.perform(post("/login/verify").param("code", emails.latestCodeFor("owner@demo.local").orElseThrow()).with(csrf()));

        assertThat(browser.perform(get("/dev/whatsapp")).getResponse().getContentAsString()).contains("Send as customer");
        MvcResult sent = browser.perform(post("/dev/whatsapp").param("phone", "0712 345 678").param("name", "Wanjiku")
                .param("kind", "text").param("text", "Nataka kuongea na mtu").with(csrf()));
        assertThat(sent.getResponse().getRedirectedUrl()).isEqualTo("/dev/whatsapp");
        jobs.drain();

        String inbox = browser.perform(get("/app/inbox")).getResponse().getContentAsString();
        assertThat(inbox).contains("Wanjiku").contains("Needs attention");
        String conversationPath = inbox.replaceAll("(?s).*href=\"(/app/conversations/[0-9a-f-]{36})\".*", "$1");
        String page = browser.perform(get(conversationPath)).getResponse().getContentAsString();
        assertThat(page).contains("Nataka kuongea na mtu").contains("Habari, karibu Demo Clinic").contains("Accept chat");

        assertThat(browser.perform(get("/app/settings")).getResponse().getContentAsString()).contains("Staff-only mode");
    }

    @Test
    void adminSetupPagesForWhatsAppHoursAndTemplatesRender() throws Exception {
        UUID demo = tenants.listAll().getFirst().id();
        ZamuPrincipal operator = new ZamuPrincipal(UUID.randomUUID(), null, ZamuRole.OPERATOR, "ops@zamu.local", "Ops");
        for (String path : new String[] {"/admin/tenants/{id}/setup/whatsapp", "/admin/tenants/{id}/setup/hours",
                "/admin/tenants/{id}/templates"}) {
            MvcResult page = mvc.perform(get(path, demo).with(TenantFixture.as(operator))).andReturn();
            assertThat(page.getResponse().getStatus()).as(path).isEqualTo(200);
        }
        assertThat(mvc.perform(get("/admin/tenants/{id}/templates", demo).with(TenantFixture.as(operator)))
                .andReturn().getResponse().getContentAsString()).contains("follow_up").contains("Approved");
    }

    @Test
    void AI_01_theDemoClinicAnswersFromItsKnowledgeBaseWithoutAnApiKey() {
        jobs.drain(); // demo entries are embedded by jobs
        TenantId demo = TenantId.of(tenants.listAll().getFirst().id());
        conversations.receive(new InboundMessage(Channel.WHATSAPP, demo, "254700111222", "Otieno", "wamid.smoke.ai",
                MessageType.TEXT, "How much is a consultation?", null, java.time.Instant.now()));

        java.util.List<String> replies = jdbc.sql("SELECT body FROM messages WHERE tenant_id = ? AND direction = 'out' ORDER BY id")
                .param(demo.bytes()).query(String.class).list();
        assertThat(replies).anySatisfy(r -> assertThat(r).contains("KES 2,000"));
    }

    @Test
    void adminPagesForKnowledgeTestsAndGoLiveRender() throws Exception {
        UUID demo = tenants.listAll().getFirst().id();
        ZamuPrincipal operator = new ZamuPrincipal(UUID.randomUUID(), null, ZamuRole.OPERATOR, "ops@zamu.local", "Ops");
        for (String path : new String[] {"/admin/tenants/{id}/kb", "/admin/tenants/{id}/eval",
                "/admin/tenants/{id}/setup/go-live", "/admin/tenants/{id}/setup/tone", "/admin/tenants/{id}/setup/services",
                "/admin/tenants/{id}/setup/calendar", "/admin/tenants/{id}/setup/hours"}) {
            MvcResult page = mvc.perform(get(path, demo).with(TenantFixture.as(operator))).andReturn();
            assertThat(page.getResponse().getStatus()).as(path).isEqualTo(200);
        }
        assertThat(mvc.perform(get("/admin/tenants/{id}/setup/go-live", demo).with(TenantFixture.as(operator)))
                .andReturn().getResponse().getContentAsString()).contains("AI provider configured");
    }

    @Test
    void milestone7PagesRenderForAdminsOperatorsAndOwners() throws Exception {
        UUID demo = tenants.listAll().getFirst().id();
        ZamuPrincipal admin = new ZamuPrincipal(UUID.randomUUID(), null, ZamuRole.ADMIN, "admin@zamu.local", "Admin");
        ZamuPrincipal owner = new ZamuPrincipal(UUID.randomUUID(), TenantId.of(demo), ZamuRole.OWNER, "o@demo.local", "Owner");
        for (String path : new String[] {"/admin/review", "/admin/change-requests", "/admin/health", "/admin/usage",
                "/admin/audit", "/admin/tenants/{id}/privacy"}) {
            MvcResult page = mvc.perform(get(path, demo).with(TenantFixture.as(admin))).andReturn();
            assertThat(page.getResponse().getStatus()).as(path).isEqualTo(200);
        }
        for (String path : new String[] {"/app/reports", "/app/requests"}) {
            MvcResult page = mvc.perform(get(path).with(TenantFixture.as(owner))).andReturn();
            assertThat(page.getResponse().getStatus()).as(path).isEqualTo(200);
        }
        assertThat(mvc.perform(get("/app/reports/weekly.csv").with(TenantFixture.as(owner))).andReturn()
                .getResponse().getContentAsString()).startsWith("metric,value\n");
    }

    @Test
    void BK_03_theDemoClinicOffersBookableTimesLocally() {
        jobs.drain();
        TenantId demo = TenantId.of(tenants.listAll().getFirst().id());
        conversations.receive(new InboundMessage(Channel.WHATSAPP, demo, "254700333444", "Akinyi", "wamid.smoke.bk",
                MessageType.TEXT, "I want to book a consultation", null, java.time.Instant.now()));

        String last = jdbc.sql("SELECT type FROM messages WHERE tenant_id = ? AND direction = 'out' ORDER BY created_at DESC, id DESC LIMIT 1")
                .param(demo.bytes()).query(String.class).single();
        assertThat(last).isIn("interactive", "text"); // times, or a handover when the clinic is closed all month
    }
}
