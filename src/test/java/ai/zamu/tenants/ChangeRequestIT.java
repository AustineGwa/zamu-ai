package ai.zamu.tenants;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import ai.zamu.shared.security.ZamuPrincipal;
import ai.zamu.shared.security.ZamuRole;
import ai.zamu.support.AbstractMySqlIT;
import ai.zamu.support.TenantFixture;
import ai.zamu.users.UserService;

import static ai.zamu.support.TenantFixture.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Owner change requests (spec: owner screens "Knowledge base ... suggest a change" and "Settings ... as change
 * requests"; admin screen "Change requests"). Owners can't edit the knowledge base themselves (KB entries are
 * approved by Zamu), so they ask, and an operator applies and closes the request.
 */
@AutoConfigureMockMvc
class ChangeRequestIT extends AbstractMySqlIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    TenantService tenants;

    @Autowired
    UserService users;

    @Autowired
    ChangeRequestService requests;

    TenantFixture fx;
    ZamuPrincipal operator;

    @BeforeEach
    void setUp() {
        fx = TenantFixture.create(tenants, users);
        operator = new ZamuPrincipal(UUID.randomUUID(), null, ZamuRole.OPERATOR, "ops@zamu.test", "Ops");
    }

    @Test
    void screens_ownerSuggestsAKnowledgeBaseChangeAndReturnsToThePage() throws Exception {
        mvc.perform(post("/app/requests").with(csrf()).with(as(fx.ownerA(), fx.tenantA()))
                        .param("type", "KB").param("entry", "Opening hours").param("details", "We open at 7:30 now")
                        .param("back", "/app/knowledge"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/app/knowledge"));

        assertThat(requests.forTenant(fx.idA())).singleElement().satisfies(r -> {
            assertThat(r.type()).isEqualTo(ChangeRequestType.KB);
            assertThat(r.details()).isEqualTo("About \"Opening hours\": We open at 7:30 now");
            assertThat(r.requestedBy()).isEqualTo(fx.ownerA().id());
            assertThat(r.open()).isTrue();
        });
        assertThat(jdbc.sql("SELECT COUNT(*) FROM audit_logs WHERE action = 'change_request.submitted'")
                .query(Integer.class).single()).isEqualTo(1);
    }

    @Test
    void screens_theReturnAddressCannotPointOffSite() throws Exception {
        for (String back : new String[] {"https://evil.example", "//evil.example", "/app/knowledge/../../logout"}) {
            mvc.perform(post("/app/requests").with(csrf()).with(as(fx.ownerA(), fx.tenantA()))
                            .param("details", "x").param("back", back))
                    .andExpect(redirectedUrl("/app/requests"));
        }
    }

    @Test
    void screens_staffCannotSendChangeRequests() throws Exception {
        mvc.perform(post("/app/requests").with(csrf()).with(as(fx.staffA(), fx.tenantA())).param("details", "x"))
                .andExpect(status().isForbidden());
        assertThat(requests.forTenant(fx.idA())).isEmpty();
    }

    @Test
    void NFR_09_ownersSeeOnlyTheirOwnRequests() throws Exception {
        requests.submit(fx.idB(), fx.ownerB().id(), ChangeRequestType.HOURS, "Baraka closes at 9pm on Fridays");
        requests.submit(fx.idA(), fx.ownerA().id(), ChangeRequestType.SERVICE, "Add a whitening service");

        assertThat(requests.forTenant(fx.idA())).extracting(ChangeRequest::details).containsExactly("Add a whitening service");
        mvc.perform(get("/app/requests").with(as(fx.ownerA(), fx.tenantA())))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Add a whitening service")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Baraka closes"))));
    }

    @Test
    void screens_operatorsSeeAllOpenRequestsAndCloseThemWithANote() throws Exception {
        UUID a = requests.submit(fx.idA(), fx.ownerA().id(), ChangeRequestType.KB, "New price: KES 2,000");
        requests.submit(fx.idB(), fx.ownerB().id(), ChangeRequestType.OTHER, "Call me");
        assertThat(requests.openCount()).isEqualTo(2);

        mvc.perform(get("/admin/change-requests").with(as(operator)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("New price: KES 2,000")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Call me")));
        mvc.perform(post("/admin/change-requests/{id}/close", a).with(csrf()).with(as(operator))
                        .param("done", "true").param("note", "Updated the fee entry"))
                .andExpect(redirectedUrl("/admin/change-requests"));

        ChangeRequest closed = requests.forTenant(fx.idA()).getFirst();
        assertThat(closed.status()).isEqualTo("done");
        assertThat(closed.note()).isEqualTo("Updated the fee entry");
        assertThat(closed.handledBy()).isEqualTo(operator.userId());
        assertThat(requests.openCount()).isEqualTo(1);
        assertThat(requests.queue(true)).extracting(ChangeRequest::details).containsExactly("Call me");

        mvc.perform(get("/admin/change-requests").with(as(fx.ownerA(), fx.tenantA()))).andExpect(status().isForbidden());
    }
}
