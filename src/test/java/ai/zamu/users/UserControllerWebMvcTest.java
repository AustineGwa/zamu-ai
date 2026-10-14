package ai.zamu.users;

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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
class UserControllerWebMvcTest {

    static final TenantId TENANT = TenantId.of(UUID.randomUUID());
    static final ZamuPrincipal OWNER = new ZamuPrincipal(UUID.randomUUID(), TENANT, ZamuRole.OWNER, "owner@afya.test", "Amina");
    static final ZamuPrincipal STAFF = new ZamuPrincipal(UUID.randomUUID(), TENANT, ZamuRole.STAFF, "staff@afya.test", "Juma");

    @Autowired
    MockMvc mvc;

    @MockitoBean
    UserService users;

    @Test
    void ownerSeesTheirStaff() throws Exception {
        when(users.listForTenant(TENANT)).thenReturn(List.of(
                new UserView(STAFF.userId(), "staff@afya.test", "Juma", ZamuRole.STAFF, null, null, Instant.now())));

        mvc.perform(get("/app/users").with(as(OWNER)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Juma")))
                .andExpect(content().string(containsString("Add login")));
    }

    @Test
    void NFR_09_theTenantComesFromTheSession() throws Exception {
        mvc.perform(get("/app/users").param("tenantId", UUID.randomUUID().toString()).with(as(OWNER)))
                .andExpect(status().isOk());
        verify(users).listForTenant(TENANT);
    }

    @Test
    void staffCannotManageLogins() throws Exception {
        mvc.perform(get("/app/users").with(as(STAFF))).andExpect(status().isForbidden());
    }

    @Test
    void invalidFormShowsInlineErrors() throws Exception {
        mvc.perform(post("/app/users").param("name", "").param("email", "nope").param("role", "STAFF")
                        .with(csrf()).with(as(OWNER)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Enter their name")))
                .andExpect(content().string(containsString("Enter a valid email address")));
    }

    @Test
    void planLimitIsShownAsAFormError() throws Exception {
        when(users.addTenantUser(eq(TENANT), any(), anyBoolean())).thenThrow(new StaffLimitReachedException(2));

        mvc.perform(post("/app/users").param("name", "Wanjiru").param("email", "w@afya.test").param("role", "STAFF")
                        .with(csrf()).with(as(OWNER)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Your plan includes 2 logins")));
    }

    @Test
    void addingAUserRedirectsBackToTheList() throws Exception {
        mvc.perform(post("/app/users").param("name", "Wanjiru").param("email", "w@afya.test").param("role", "STAFF")
                        .with(csrf()).with(as(OWNER)))
                .andExpect(redirectedUrl("/app/users"));
        verify(users).addTenantUser(eq(TENANT), any(), eq(true));
    }
}
