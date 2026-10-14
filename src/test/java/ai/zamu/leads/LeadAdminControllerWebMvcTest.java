package ai.zamu.leads;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import ai.zamu.shared.ids.TenantId;
import ai.zamu.shared.security.SecurityConfig;
import ai.zamu.shared.security.ZamuPrincipal;
import ai.zamu.shared.security.ZamuRole;

import static ai.zamu.support.TenantFixture.as;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LeadAdminController.class)
@Import(SecurityConfig.class)
class LeadAdminControllerWebMvcTest {

    static final ZamuPrincipal OPERATOR = new ZamuPrincipal(UUID.randomUUID(), null, ZamuRole.OPERATOR, "ops@zamu.test", "Ops");
    static final ZamuPrincipal OWNER = new ZamuPrincipal(UUID.randomUUID(), TenantId.of(UUID.randomUUID()), ZamuRole.OWNER, "o@x.test", "O");

    @Autowired
    MockMvc mvc;

    @MockitoBean
    LeadService leads;

    @Test
    void operatorsSeeSignUps() throws Exception {
        when(leads.recent(null)).thenReturn(List.of(new LeadView(UUID.randomUUID(), "Afya Bora Clinic", "Jane",
                "254712345678", "0712 345 678", null, BusinessType.CLINIC, null, LeadStatus.NEW, Instant.now())));

        mvc.perform(get("/admin/leads").with(as(OPERATOR)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Afya Bora Clinic")))
                .andExpect(content().string(containsString("tel:+254712345678")));
    }

    @Test
    void operatorsChangeTheStatus() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(post("/admin/leads/{id}/status", id).param("status", "CONTACTED").with(csrf()).with(as(OPERATOR)))
                .andExpect(redirectedUrl("/admin/leads"));
        verify(leads).changeStatus(id, LeadStatus.CONTACTED);
    }

    @Test
    void tenantUsersCannotSeeSignUps() throws Exception {
        mvc.perform(get("/admin/leads").with(as(OWNER))).andExpect(status().isForbidden());
    }
}
