package ai.zamu.support;

import jakarta.servlet.http.Cookie;

import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Carries the Spring Session cookie between MockMvc requests, like a browser, so login tests exercise the real
 * MySQL-backed session (NFR-08).
 */
public class Browser {

    public static final String SESSION_COOKIE = "ZAMU_SESSION";

    private final MockMvc mvc;
    private Cookie session;

    public Browser(MockMvc mvc) {
        this.mvc = mvc;
    }

    public MvcResult perform(MockHttpServletRequestBuilder request) throws Exception {
        if (session != null) {
            request.cookie(session);
        }
        MvcResult result = mvc.perform(request).andReturn();
        Cookie updated = result.getResponse().getCookie(SESSION_COOKIE);
        if (updated != null) {
            session = updated.getMaxAge() == 0 ? null : updated;
        }
        return result;
    }

    public Cookie sessionCookie() {
        return session;
    }
}
