package ai.zamu.pricing;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({PricingController.class, PricingAdminController.class})
@Import(SecurityConfig.class)
class PricingControllerWebMvcTest {

    static final PricingPlan ENTERPRISE = new PricingPlan(UUID.randomUUID(), "enterprise", "Enterprise",
            "Zamu on your own infrastructure.", "For banks.", new BigDecimal("120000.00"), "KES", "per month", 30,
            List.of("Installed on your own servers"), "Book your 1-month pilot", null, true, 40, true);

    @Autowired
    MockMvc mvc;

    @MockitoBean
    PricingService pricing;

    @Test
    void thePricingPageIsPublicAndShowsWhatTheDatabaseHolds() throws Exception {
        when(pricing.catalog()).thenReturn(new PricingCatalog(List.of(ENTERPRISE),
                List.of(new PricingCatalog.ComparisonGroup("Security and hosting", List.of(
                        new PricingCatalog.ComparisonRow("Data stays inside your network", Map.of("enterprise", "yes")),
                        new PricingCatalog.ComparisonRow("Hosting", Map.of("enterprise", "Your servers"))))),
                List.of(new PricingCatalog.Faq("What does on-premises hosting involve?", "We install Zamu for you."))));

        mvc.perform(get("/pricing"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Enterprise")))
                .andExpect(content().string(containsString("120,000")))
                .andExpect(content().string(containsString("1-month free trial")))
                .andExpect(content().string(containsString("Book your 1-month pilot")))
                .andExpect(content().string(containsString("href=\"/signup?plan=enterprise\"")))
                .andExpect(content().string(containsString("Data stays inside your network")))
                .andExpect(content().string(containsString("Your servers")))
                .andExpect(content().string(containsString("What does on-premises hosting involve?")));
    }

    @Test
    void anEmptyPriceListStillRenders() throws Exception {
        when(pricing.catalog()).thenReturn(new PricingCatalog(List.of(), List.of(), List.of()));
        mvc.perform(get("/pricing")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Our plans are being updated")));
    }

    @Test
    void onlyTheZamuTeamEditsThePriceList() throws Exception {
        ZamuPrincipal owner = new ZamuPrincipal(UUID.randomUUID(), TenantId.of(UUID.randomUUID()), ZamuRole.OWNER, "o@x.test", "O");
        ZamuPrincipal operator = new ZamuPrincipal(UUID.randomUUID(), null, ZamuRole.OPERATOR, "ops@zamu.test", "Ops");
        when(pricing.allPlans()).thenReturn(List.of(ENTERPRISE));

        mvc.perform(get("/admin/pricing").with(as(owner))).andExpect(status().isForbidden());
        mvc.perform(get("/admin/pricing")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/admin/pricing").with(as(operator))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Enterprise")));
        mvc.perform(get("/admin/pricing/{id}", ENTERPRISE.id()).with(as(operator))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Installed on your own servers")));
    }
}
