package ai.zamu.users;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import ai.zamu.shared.crypto.Totp;
import ai.zamu.support.AbstractMySqlIT;
import ai.zamu.support.Browser;
import ai.zamu.support.TenantFixture;
import ai.zamu.tenants.TenantService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Email one-time-code login end to end, with sessions in MySQL (spec: Login screen, Auth routes, NFR-08). */
@AutoConfigureMockMvc
class LoginFlowIT extends AbstractMySqlIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    TenantService tenants;

    @Autowired
    UserService users;

    @Autowired
    LoginService logins;

    @Autowired
    LoginCodeService codes;

    TenantFixture fx;
    Browser browser;

    @BeforeEach
    void setUp() {
        fx = TenantFixture.create(tenants, users);
        browser = new Browser(mvc);
    }

    @Test
    void ownerLogsInWithAnEmailedCode() throws Exception {
        MvcResult requested = requestCode("Owner@Afya.test ");
        assertThat(requested.getResponse().getRedirectedUrl()).isEqualTo("/login/verify");

        String code = emails.latestCodeFor("owner@afya.test").orElseThrow();
        MvcResult verified = submitCode(code);
        assertThat(verified.getResponse().getRedirectedUrl()).isEqualTo("/");

        MvcResult page = browser.perform(get("/app/users"));
        assertThat(page.getResponse().getStatus()).isEqualTo(200);
        assertThat(page.getResponse().getContentAsString()).contains("Juma Staff");
    }

    @Test
    void NFR_08_sessionIsStoredInMySqlWithTwelveHourTimeout() throws Exception {
        requestCode("owner@afya.test");
        submitCode(emails.latestCodeFor("owner@afya.test").orElseThrow());

        Map<String, Object> row = jdbc.sql("SELECT MAX_INACTIVE_INTERVAL, PRINCIPAL_NAME FROM SPRING_SESSION WHERE PRINCIPAL_NAME IS NOT NULL")
                .query().singleRow();
        assertThat(((Number) row.get("MAX_INACTIVE_INTERVAL")).intValue()).isEqualTo(12 * 60 * 60);
        assertThat(row.get("PRINCIPAL_NAME")).as("principal is the user id, not the email").asString().doesNotContain("@");
    }

    @Test
    void Login_codeExpiresAfter10Minutes() throws Exception {
        requestCode("owner@afya.test");
        String code = emails.latestCodeFor("owner@afya.test").orElseThrow();

        clock.advance(Duration.ofMinutes(10).plusSeconds(1));

        MvcResult result = submitCode(code);
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getContentAsString()).contains("wrong or has expired");
    }

    @Test
    void Login_aCodeWorksOnlyOnce() {
        codes.requestCode("owner@afya.test");
        String code = emails.latestCodeFor("owner@afya.test").orElseThrow();

        assertThat(codes.verify("owner@afya.test", code)).isPresent();
        assertThat(codes.verify("owner@afya.test", code)).isEmpty();
    }

    @Test
    void Login_fiveWrongGuessesLockTheCode() throws Exception {
        requestCode("owner@afya.test");
        String code = emails.latestCodeFor("owner@afya.test").orElseThrow();
        String wrong = code.equals("000000") ? "111111" : "000000";
        for (int i = 0; i < 5; i++) {
            submitCode(wrong);
        }
        MvcResult result = submitCode(code);
        assertThat(result.getResponse().getRedirectedUrl()).as("right code after 5 wrong guesses").isNull();
    }

    @Test
    void Login_rateLimitedTo5CodesPerHourPerEmail() throws Exception {
        for (int i = 0; i < 5; i++) {
            assertThat(requestCode("owner@afya.test").getResponse().getRedirectedUrl()).isEqualTo("/login/verify");
        }
        MvcResult sixth = requestCode("owner@afya.test");
        assertThat(sixth.getResponse().getContentAsString()).contains("Too many codes");
        assertThat(emails.sentTo("owner@afya.test")).hasSize(5);

        clock.advance(Duration.ofHours(1).plusSeconds(1));
        assertThat(requestCode("owner@afya.test").getResponse().getRedirectedUrl()).isEqualTo("/login/verify");
    }

    @Test
    void Login_unknownEmailLooksTheSameButNoEmailIsSent() throws Exception {
        MvcResult result = requestCode("nobody@nowhere.test");
        assertThat(result.getResponse().getRedirectedUrl()).isEqualTo("/login/verify");
        assertThat(emails.sent()).isEmpty();
    }

    @Test
    void Login_disabledUserGetsNoCodeAndCannotUseAnOldOne() throws Exception {
        codes.requestCode("staff@afya.test");
        String oldCode = emails.latestCodeFor("staff@afya.test").orElseThrow();
        users.disable(fx.idA(), fx.staffA().id(), fx.ownerA().id());
        emails.clear();

        requestCode("staff@afya.test");
        assertThat(emails.sent()).isEmpty();
        assertThat(codes.verify("staff@afya.test", oldCode)).isEmpty();
    }

    @Test
    void disablingAUserEndsTheirSessionsImmediately() throws Exception {
        requestCode("staff@afya.test");
        submitCode(emails.latestCodeFor("staff@afya.test").orElseThrow());
        assertThat(browser.perform(get("/app/inbox")).getResponse().getStatus()).isEqualTo(200);

        users.disable(fx.idA(), fx.staffA().id(), fx.ownerA().id());

        assertThat(browser.perform(get("/app/inbox")).getResponse().getRedirectedUrl()).endsWith("/login");
    }

    @Test
    void NFR_08_operatorMustSetUpAndUseAnAuthenticatorApp() throws Exception {
        logins.ensureBootstrapAdmin("admin@zamu.test", "Ops Admin");

        requestCode("admin@zamu.test");
        MvcResult afterEmailCode = submitCode(emails.latestCodeFor("admin@zamu.test").orElseThrow());
        assertThat(afterEmailCode.getResponse().getRedirectedUrl()).isEqualTo("/login/totp/setup");

        // Not logged in yet: the console still redirects to login.
        assertThat(browser.perform(get("/admin/tenants")).getResponse().getRedirectedUrl()).endsWith("/login");

        MvcResult setup = browser.perform(get("/login/totp/setup"));
        String secret = ((String) setup.getModelAndView().getModel().get("secretGrouped")).replace(" ", "");
        String code = Totp.codeAt(secret, Totp.step(clock.instant()));

        MvcResult enrolled = browser.perform(post("/login/totp/setup").param("code", code).with(csrf()));
        assertThat(enrolled.getResponse().getRedirectedUrl()).isEqualTo("/");
        assertThat(browser.perform(get("/admin/tenants")).getResponse().getStatus()).isEqualTo(200);

        // Next login asks for the app code, and the same code can't be replayed.
        Browser next = new Browser(mvc);
        next.perform(post("/login/code").param("email", "admin@zamu.test").with(csrf()));
        MvcResult emailStep = next.perform(post("/login/verify")
                .param("code", emails.latestCodeFor("admin@zamu.test").orElseThrow()).with(csrf()));
        assertThat(emailStep.getResponse().getRedirectedUrl()).isEqualTo("/login/totp");

        MvcResult replay = next.perform(post("/login/totp").param("code", code).with(csrf()));
        assertThat(replay.getResponse().getRedirectedUrl()).isNull();

        clock.advance(Duration.ofSeconds(Totp.STEP_SECONDS));
        String fresh = Totp.codeAt(secret, Totp.step(clock.instant()));
        MvcResult ok = next.perform(post("/login/totp").param("code", fresh).with(csrf()));
        assertThat(ok.getResponse().getRedirectedUrl()).isEqualTo("/");
    }

    @Test
    void NFR_14_loginIsAudited() throws Exception {
        requestCode("staff@afya.test");
        submitCode(emails.latestCodeFor("staff@afya.test").orElseThrow());
        Integer logins = jdbc.sql("SELECT COUNT(*) FROM audit_logs WHERE action = 'user.login'").query(Integer.class).single();
        assertThat(logins).isEqualTo(1);
    }

    @Test
    void NFR_07_postWithoutCsrfTokenIsRejected() throws Exception {
        MvcResult result = browser.perform(post("/login/code").param("email", "owner@afya.test"));
        assertThat(result.getResponse().getStatus()).isEqualTo(403);
    }

    private MvcResult requestCode(String email) throws Exception {
        return browser.perform(post("/login/code").param("email", email).with(csrf()));
    }

    private MvcResult submitCode(String code) throws Exception {
        return browser.perform(post("/login/verify").param("code", code).with(csrf()));
    }
}
