package ai.zamu.pricing;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PricingPlanTest {

    private static PricingPlan plan(String price, int trialDays) {
        return new PricingPlan(UUID.randomUUID(), "x", "X", "t", "a", new BigDecimal(price), "KES", "per month",
                trialDays, List.of("one"), "Try", null, false, 10, true);
    }

    @Test
    void pricesShowThousandsSeparatorsAndCentsOnlyWhenNeeded() {
        assertThat(plan("3500.00", 7).priceDisplay()).isEqualTo("3,500");
        assertThat(plan("120000.00", 7).priceDisplay()).isEqualTo("120,000");
        assertThat(plan("999.50", 7).priceDisplay()).isEqualTo("999.50");
    }

    @Test
    void trialsReadTheWayTheyAreSold() {
        assertThat(plan("1", 7).trialLabel()).isEqualTo("1-week free trial");
        assertThat(plan("1", 14).trialLabel()).isEqualTo("2-week free trial");
        assertThat(plan("1", 30).trialLabel()).isEqualTo("1-month free trial");
        assertThat(plan("1", 10).trialLabel()).isEqualTo("10-day free trial");
        assertThat(plan("1", 0).trialLabel()).isNull();
        assertThat(plan("1", 14).trialWords()).isEqualTo("2 weeks");
    }
}
