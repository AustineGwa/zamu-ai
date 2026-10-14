package ai.zamu.tenants;

import java.math.BigDecimal;
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
import ai.zamu.shared.security.AdminTenantScope;
import ai.zamu.shared.security.SecurityConfig;
import ai.zamu.shared.security.ZamuPrincipal;
import ai.zamu.shared.security.ZamuRole;

import static ai.zamu.support.TenantFixture.as;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TenantController.class)
@Import({SecurityConfig.class, AdminTenantScope.class})
class TenantControllerWebMvcTest {

    static final ZamuPrincipal OPERATOR = new ZamuPrincipal(UUID.randomUUID(), null, ZamuRole.OPERATOR, "ops@zamu.test", "Ops");
    static final ZamuPrincipal STAFF = new ZamuPrincipal(UUID.randomUUID(), TenantId.of(UUID.randomUUID()), ZamuRole.STAFF, "s@x.test", "Staff");

    @Autowired
    MockMvc mvc;

    @MockitoBean
    TenantService tenants;

    static TenantView tenant(UUID id, String displayName) {
        return new TenantView(id, displayName + " Ltd", displayName, TenantPlan.GROWTH, TenantStatus.ONBOARDING,
                "Africa/Nairobi", TenantLanguage.EN, TenantTone.FRIENDLY, 1500, new BigDecimal("0.60"), Instant.now());
    }

    @Test
    void listShowsTenantsAndTheCreateForm() throws Exception {
        when(tenants.listAll()).thenReturn(List.of(tenant(UUID.randomUUID(), "Afya Clinic")));

        mvc.perform(get("/admin/tenants").with(as(OPERATOR)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Afya Clinic")))
                .andExpect(content().string(containsString("Create and start setup")));
    }

    @Test
    void createWithMissingFieldsShowsInlineErrors() throws Exception {
        mvc.perform(post("/admin/tenants").param("name", "").param("displayName", "").param("plan", "GROWTH")
                        .with(csrf()).with(as(OPERATOR)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Enter the registered business name")))
                .andExpect(content().string(containsString("Enter the name customers use")));
        verify(tenants, never()).create(any());
    }

    @Test
    void createRedirectsToTheFirstWizardStep() throws Exception {
        UUID id = UUID.randomUUID();
        when(tenants.create(any())).thenReturn(tenant(id, "Afya Clinic"));

        mvc.perform(post("/admin/tenants").param("name", "Afya Clinic Ltd").param("displayName", "Afya Clinic")
                        .param("plan", "GROWTH").with(csrf()).with(as(OPERATOR)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/tenants/" + id + "/setup/profile"));
    }

    @Test
    void duplicateNameIsShownOnTheField() throws Exception {
        when(tenants.create(any())).thenThrow(new DuplicateTenantNameException());

        mvc.perform(post("/admin/tenants").param("name", "Afya Clinic Ltd").param("displayName", "Afya Clinic")
                        .param("plan", "GROWTH").with(csrf()).with(as(OPERATOR)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("already exists")));
    }

    @Test
    void profileStepRendersWithTheWizard() throws Exception {
        UUID id = UUID.randomUUID();
        when(tenants.get(eq(TenantId.of(id)))).thenReturn(tenant(id, "Afya Clinic"));

        mvc.perform(get("/admin/tenants/{id}/setup/profile", id).with(as(OPERATOR)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Business profile")))
                .andExpect(content().string(containsString("aria-current=\"step\"")));
    }

    @Test
    void laterStepsSayWhichMilestoneBuildsThem() throws Exception {
        UUID id = UUID.randomUUID();
        when(tenants.get(eq(TenantId.of(id)))).thenReturn(tenant(id, "Afya Clinic"));

        mvc.perform(get("/admin/tenants/{id}/setup/mpesa", id).with(as(OPERATOR)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Milestone 6")));
    }

    @Test
    void tenantStaffAreForbidden() throws Exception {
        mvc.perform(get("/admin/tenants").with(as(STAFF))).andExpect(status().isForbidden());
    }
}
