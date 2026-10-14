package ai.zamu.pricing;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import ai.zamu.support.AbstractMySqlIT;

import static org.assertj.core.api.Assertions.assertThat;

/** The seeded price list (V15__pricing_create.sql) and editing it, on MySQL. */
class PricingServiceIT extends AbstractMySqlIT {

    @Autowired
    PricingService pricing;

    @Test
    void theFourTiersAreSeededInOrderWithTheirTrials() {
        PricingCatalog catalog = pricing.catalog();

        assertThat(catalog.plans()).extracting(PricingPlan::code)
                .containsExactly("starter", "growth", "business", "enterprise");
        assertThat(catalog.plans()).extracting(PricingPlan::priceDisplay)
                .containsExactly("3,500", "20,000", "75,000", "120,000");
        assertThat(catalog.plans()).extracting(PricingPlan::trialLabel)
                .containsExactly("1-week free trial", "2-week free trial", "1-month free trial", "1-month free trial");
        assertThat(catalog.plans()).allSatisfy(p -> assertThat(p.highlights()).isNotEmpty());
        assertThat(catalog.comparison()).extracting(PricingCatalog.ComparisonGroup::name).contains("Channels",
                "Security and hosting");
        assertThat(catalog.faqs()).isNotEmpty();
    }

    @Test
    void theComparisonMatchesTheTiers() {
        PricingCatalog.ComparisonRow voice = row("Voice receptionist on your business phone line");
        assertThat(voice.excluded("starter")).as("tier 1 is WhatsApp only").isTrue();
        assertThat(voice.included("growth")).isTrue();

        PricingCatalog.ComparisonRow onPrem = row("Data stays inside your network");
        assertThat(onPrem.included("enterprise")).as("tier 4: on-premises").isTrue();
        assertThat(onPrem.excluded("business")).isTrue();
    }

    @Test
    void anAdminEditShowsOnTheWebsiteStraightAwayAndAHiddenPlanDisappears() {
        PricingPlan starter = pricing.findActive("starter").orElseThrow();
        PricingPlanForm original = PricingPlanForm.from(starter);
        try {
            pricing.updatePlan(starter.id(), new PricingPlanForm(original.name(), original.tagline(), original.audience(),
                    new BigDecimal("4000.00"), original.priceNote(), 10, original.highlights(), original.ctaLabel(),
                    "", false, original.sortOrder(), true));
            assertThat(pricing.findActive("starter").orElseThrow().priceDisplay()).isEqualTo("4,000");
            assertThat(pricing.findActive("starter").orElseThrow().trialLabel()).isEqualTo("10-day free trial");

            pricing.updatePlan(starter.id(), new PricingPlanForm(original.name(), original.tagline(), original.audience(),
                    original.monthlyPrice(), original.priceNote(), original.trialDays(), original.highlights(),
                    original.ctaLabel(), original.badge(), original.featured(), original.sortOrder(), false));
            assertThat(pricing.findActive("starter")).isEmpty();
            assertThat(pricing.namesByCode()).containsEntry("starter", "Starter");
            assertThat(jdbc.sql("SELECT COUNT(*) FROM audit_logs WHERE action = 'pricing.plan_updated'")
                    .query(Integer.class).single()).isEqualTo(2);
        } finally {
            pricing.updatePlan(starter.id(), original); // the price list is shared by every test
        }
    }

    private PricingCatalog.ComparisonRow row(String label) {
        return pricing.catalog().comparison().stream().flatMap(g -> g.rows().stream())
                .filter(r -> r.label().equals(label)).findFirst().orElseThrow();
    }
}
