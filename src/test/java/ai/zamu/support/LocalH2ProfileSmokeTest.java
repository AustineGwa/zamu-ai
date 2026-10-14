package ai.zamu.support;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

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

    @Test
    void withNoProfileSetTheDefaultIsH2() {
        assertThat(environment.getActiveProfiles()).isEmpty();
        assertThat(environment.getDefaultProfiles()).containsExactly("h2");
    }

    @Test
    void visitorsSeeTheLandingPageAndCanSignUp() throws Exception {
        assertThat(mvc.perform(get("/")).andReturn().getResponse().getContentAsString())
                .contains("Never miss a customer on WhatsApp again");

        MvcResult signedUp = mvc.perform(post("/signup").with(csrf())
                .param("businessName", "Smoke Test Salon").param("contactName", "Wanjiru")
                .param("phone", "0712 345 678").param("businessType", "SALON").param("consent", "true")).andReturn();
        assertThat(signedUp.getResponse().getRedirectedUrl()).isEqualTo("/signup/thanks");
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
}
