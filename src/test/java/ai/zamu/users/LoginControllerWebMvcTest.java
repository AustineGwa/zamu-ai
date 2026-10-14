package ai.zamu.users;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import ai.zamu.shared.security.SecurityConfig;
import ai.zamu.shared.security.SessionLogin;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LoginController.class)
@Import({SecurityConfig.class, LoginControllerWebMvcTest.FixedClock.class})
class LoginControllerWebMvcTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    LoginCodeService codes;

    @MockitoBean
    LoginService logins;

    @MockitoBean
    SessionLogin sessionLogin;

    @Test
    void loginPageRenders() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Email me a code")))
                .andExpect(content().string(containsString("name=\"_csrf\"")));
    }

    @Test
    void invalidEmailShowsAnInlineError() throws Exception {
        mvc.perform(post("/login/code").param("email", "not-an-email").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Enter a valid email address")));
        verify(codes, never()).requestCode(anyString());
    }

    @Test
    void validEmailRequestsACodeAndKeepsTheEmailOutOfTheUrl() throws Exception {
        when(codes.requestCode("owner@afya.test")).thenReturn(LoginCodeService.RequestOutcome.ACCEPTED);

        mvc.perform(post("/login/code").param("email", "owner@afya.test").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login/verify"));
    }

    @Test
    void rateLimitIsExplained() throws Exception {
        when(codes.requestCode("owner@afya.test")).thenReturn(LoginCodeService.RequestOutcome.RATE_LIMITED);

        mvc.perform(post("/login/code").param("email", "owner@afya.test").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Too many codes")));
    }

    @Test
    void verifyPageNeedsAnEmailFirst() throws Exception {
        mvc.perform(get("/login/verify")).andExpect(redirectedUrl("/login"));
    }

    @Test
    void verifyPageShowsTheEmailFromTheSession() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(LoginController.EMAIL_KEY, "owner@afya.test");

        mvc.perform(get("/login/verify").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("owner@afya.test")));
    }

    @Test
    void wrongCodeShowsAnInlineError() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(LoginController.EMAIL_KEY, "owner@afya.test");

        mvc.perform(post("/login/verify").param("code", "123456").session(session).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("wrong or has expired")));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClock {
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-10-14T06:00:00Z"), ZoneOffset.UTC);
        }
    }
}
