package ai.zamu.leads;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import ai.zamu.shared.ids.TenantId;
import ai.zamu.shared.security.SecurityConfig;
import ai.zamu.shared.security.ZamuPrincipal;
import ai.zamu.shared.security.ZamuRole;

import static ai.zamu.support.TenantFixture.as;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The public website: landing page and sign-up form. */
@WebMvcTest({LandingController.class, SignUpController.class})
@Import({SecurityConfig.class, SignUpRateLimiter.class})
class PublicSiteWebMvcTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    LeadService leads;

    @Test
    void visitorsSeeTheLandingPageWithSignUpAndSignIn() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Never miss a customer on WhatsApp again")))
                .andExpect(content().string(containsString("href=\"/signup\"")))
                .andExpect(content().string(containsString("href=\"/login\"")))
                .andExpect(content().string(containsString("KES 15,000")));
    }

    @Test
    void signedInUsersSkipTheLandingPage() throws Exception {
        ZamuPrincipal owner = new ZamuPrincipal(UUID.randomUUID(), TenantId.of(UUID.randomUUID()), ZamuRole.OWNER, "o@x.test", "O");
        ZamuPrincipal admin = new ZamuPrincipal(UUID.randomUUID(), null, ZamuRole.ADMIN, "a@zamu.test", "A");
        mvc.perform(get("/").with(as(owner))).andExpect(redirectedUrl("/app"));
        mvc.perform(get("/").with(as(admin))).andExpect(redirectedUrl("/admin/tenants"));
    }

    @Test
    void signUpFormIsPublic() throws Exception {
        mvc.perform(get("/signup"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Sign up for Zamu")));
    }

    @Test
    void missingFieldsShowInlineErrors() throws Exception {
        mvc.perform(post("/signup").param("businessName", "").param("contactName", "").param("phone", "").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Enter your business name")))
                .andExpect(content().string(containsString("Choose the kind of business")))
                .andExpect(content().string(containsString("Tick the box so we can contact you")));
        verify(leads, never()).signUp(any());
    }

    @Test
    void aValidSignUpIsSavedAndThanked() throws Exception {
        mvc.perform(valid()).andExpect(redirectedUrl("/signup/thanks"));
        verify(leads).signUp(any());
        mvc.perform(get("/signup/thanks")).andExpect(content().string(containsString("Thanks, you're signed up")));
    }

    @Test
    void PAY_04_aNonKenyanNumberIsShownOnThePhoneField() throws Exception {
        when(leads.signUp(any())).thenThrow(new InvalidPhoneException());
        mvc.perform(valid()).andExpect(status().isOk())
                .andExpect(content().string(containsString("Enter a Kenyan mobile number")));
    }

    @Test
    void theHoneypotDropsBotsSilently() throws Exception {
        mvc.perform(valid().param("website", "http://spam.example"))
                .andExpect(redirectedUrl("/signup/thanks"));
        verify(leads, never()).signUp(any());
    }

    @Test
    void sixthSignUpFromOneAddressInAnHourIsRefused() throws Exception {
        for (int i = 0; i < SignUpRateLimiter.MAX_PER_HOUR; i++) {
            mvc.perform(valid().with(r -> { r.setRemoteAddr("10.9.8.7"); return r; }))
                    .andExpect(redirectedUrl("/signup/thanks"));
        }
        mvc.perform(valid().with(r -> { r.setRemoteAddr("10.9.8.7"); return r; }))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("try again in an hour")));
    }

    private static MockHttpServletRequestBuilder valid() {
        return post("/signup")
                .param("businessName", "Afya Bora Clinic")
                .param("contactName", "Jane Wanjiku")
                .param("phone", "0712 345 678")
                .param("businessType", "CLINIC")
                .param("consent", "true")
                .with(csrf());
    }
}
