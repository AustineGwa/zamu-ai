package ai.zamu.support;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import ai.zamu.conversations.Channel;
import ai.zamu.conversations.ConversationService;
import ai.zamu.conversations.InboundMessage;
import ai.zamu.conversations.MessageType;
import ai.zamu.shared.ids.TenantId;
import ai.zamu.shared.ids.Uuids;
import ai.zamu.shared.security.ZamuPrincipal;
import ai.zamu.shared.security.ZamuRole;
import ai.zamu.tenants.TenantService;
import ai.zamu.users.UserService;

import static ai.zamu.support.TenantFixture.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Route-level tenant isolation (spec: Testing, "for every route and repository method, a user of tenant A gets
 * 404 or 403 for tenant B's resources"; NFR-09). Add a test here for every new route.
 */
@AutoConfigureMockMvc
class TenantIsolationIT extends AbstractMySqlIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    TenantService tenants;

    @Autowired
    UserService users;

    @Autowired
    ConversationService conversations;

    TenantFixture fx;

    @BeforeEach
    void fixture() {
        fx = TenantFixture.create(tenants, users);
    }

    @Test
    void NFR_09_ownerOfTenantA_listsOnlyTheirOwnUsers() throws Exception {
        mvc.perform(get("/app/users").with(as(fx.ownerA(), fx.tenantA())))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("staff@afya.test")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("baraka.test"))));
    }

    @Test
    void NFR_09_ownerOfTenantA_cannotDisableAUserOfTenantB() throws Exception {
        mvc.perform(post("/app/users/{id}/disable", fx.staffB().id()).with(csrf()).with(as(fx.ownerA(), fx.tenantA())))
                .andExpect(status().isNotFound());

        assertThat(users.listForTenant(fx.idB())).allMatch(u -> u.active());
    }

    @Test
    void NFR_09_tenantIdInTheRequestIsIgnored() throws Exception {
        // A crafted tenantId parameter must not change which tenant's users are listed.
        mvc.perform(get("/app/users").param("tenantId", fx.tenantB().id().toString()).with(as(fx.ownerA(), fx.tenantA())))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("baraka.test"))));
    }

    @Test
    void NFR_09_staff_getForbiddenOnOwnerAndAdminRoutes() throws Exception {
        mvc.perform(get("/app/users").with(as(fx.staffA(), fx.tenantA()))).andExpect(status().isForbidden());
        mvc.perform(get("/admin/tenants").with(as(fx.staffA(), fx.tenantA()))).andExpect(status().isForbidden());
        mvc.perform(get("/admin/tenants/{id}/setup/profile", fx.tenantB().id()).with(as(fx.ownerA(), fx.tenantA())))
                .andExpect(status().isForbidden());
    }

    @Test
    void NFR_09_operatorsReachAnyTenantThroughAdminRoutes() throws Exception {
        ZamuPrincipal operator = new ZamuPrincipal(UUID.randomUUID(), null, ZamuRole.OPERATOR, "ops@zamu.test", "Ops");
        mvc.perform(get("/admin/tenants/{id}/setup/staff", fx.tenantB().id()).with(as(operator)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("staff@baraka.test")));
        mvc.perform(get("/admin/tenants/{id}/setup/profile", UUID.randomUUID()).with(as(operator)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/admin/tenants/not-a-uuid/setup/profile").with(as(operator)))
                .andExpect(status().isNotFound());
    }

    @Test
    void operatorsCannotUseTheTenantDashboard() throws Exception {
        ZamuPrincipal operator = new ZamuPrincipal(UUID.randomUUID(), null, ZamuRole.OPERATOR, "ops@zamu.test", "Ops");
        mvc.perform(get("/app/users").with(as(operator))).andExpect(status().isForbidden());
    }

    @Test
    void anonymousVisitorsAreSentToLogin() throws Exception {
        mvc.perform(get("/app/users")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/admin/tenants")).andExpect(status().is3xxRedirection());
    }

    // ---- Milestone 2: inbox, conversations, handovers, settings, WhatsApp admin ----------------------

    @Test
    void NFR_09_ownerOfTenantA_seesOnlyTheirOwnInbox() throws Exception {
        customerWrites(fx.idA(), "254711111111", "Akinyi");
        customerWrites(fx.idB(), "254722222222", "Brenda");
        mvc.perform(get("/app/inbox").with(as(fx.staffA(), fx.tenantA())))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Akinyi")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Brenda"))));
        mvc.perform(get("/app/inbox/list").param("q", "Brenda").with(as(fx.staffA(), fx.tenantA())))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Brenda"))));
    }

    @Test
    void NFR_09_userOfTenantA_getsNotFoundForTenantBsConversation() throws Exception {
        UUID conversationOfB = customerWrites(fx.idB(), "254722222222", "Brenda");
        UUID customerOfB = jdbc.sql("SELECT customer_id FROM conversations WHERE id = ?")
                .param(Uuids.toBytes(conversationOfB)).query((rs, n) -> Uuids.read(rs, "customer_id")).single();
        var userA = as(fx.staffA(), fx.tenantA());

        mvc.perform(get("/app/conversations/{id}", conversationOfB).with(userA)).andExpect(status().isNotFound());
        mvc.perform(get("/app/conversations/{id}/messages", conversationOfB).with(userA)).andExpect(status().isNotFound());
        mvc.perform(post("/app/conversations/{id}/messages", conversationOfB).param("text", "hi").with(csrf()).with(userA))
                .andExpect(status().isNotFound());
        mvc.perform(post("/app/conversations/{id}/accept", conversationOfB).with(csrf()).with(userA))
                .andExpect(status().isNotFound());
        mvc.perform(post("/app/conversations/{id}/release", conversationOfB).with(csrf()).with(userA))
                .andExpect(status().isNotFound());
        mvc.perform(post("/app/conversations/{id}/close", conversationOfB).with(csrf()).with(userA))
                .andExpect(status().isNotFound());
        mvc.perform(post("/app/customers/{id}/block", customerOfB).param("blocked", "true")
                        .param("conversationId", conversationOfB.toString()).with(csrf()).with(userA))
                .andExpect(status().isNotFound());

        assertThat(jdbc.sql("SELECT COUNT(*) FROM messages WHERE sender = 'staff'").query(Integer.class).single()).isZero();
        assertThat(jdbc.sql("SELECT COUNT(*) FROM customers WHERE blocked = TRUE").query(Integer.class).single()).isZero();
    }

    @Test
    void NFR_09_settingsAreOwnerOnlyAndWhatsAppSetupIsAdminOnly() throws Exception {
        mvc.perform(get("/app/settings").with(as(fx.staffA(), fx.tenantA()))).andExpect(status().isForbidden());
        mvc.perform(post("/app/settings/staff-only").param("enabled", "true").with(csrf()).with(as(fx.staffA(), fx.tenantA())))
                .andExpect(status().isForbidden());
        mvc.perform(get("/app/settings").with(as(fx.ownerA(), fx.tenantA()))).andExpect(status().isOk());
        for (String path : new String[] {"/admin/tenants/{id}/setup/whatsapp", "/admin/tenants/{id}/setup/hours",
                "/admin/tenants/{id}/templates"}) {
            mvc.perform(get(path, fx.tenantA().id()).with(as(fx.ownerA(), fx.tenantA()))).andExpect(status().isForbidden());
        }
    }

    @Test
    void theWhatsAppSimulatorDoesNotExistOutsideLocalDevelopment() throws Exception {
        mvc.perform(get("/dev/whatsapp").with(as(fx.ownerA(), fx.tenantA()))).andExpect(status().isNotFound());
    }

    private UUID customerWrites(TenantId tenantId, String phone, String name) {
        conversations.receive(new InboundMessage(Channel.WHATSAPP, tenantId, phone, name, "wamid." + UUID.randomUUID(),
                MessageType.TEXT, "Hello", null, clock.instant()));
        return jdbc.sql("SELECT id FROM conversations WHERE tenant_id = ?").param(tenantId.bytes())
                .query((rs, n) -> Uuids.read(rs, "id")).single();
    }
}
