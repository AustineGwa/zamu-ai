package ai.zamu.pricing;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * A plan on the public price list (pricing_plans). Everything shown on the pricing page comes from the database,
 * so the Zamu team can change prices, trials and wording in the admin console without a release.
 */
public record PricingPlan(UUID id, String code, String name, String tagline, String audience, BigDecimal monthlyPrice,
        String currency, String priceNote, int trialDays, List<String> highlights, String ctaLabel, String badge,
        boolean featured, int sortOrder, boolean active) {

    public PricingPlan {
        highlights = highlights == null ? List.of() : List.copyOf(highlights);
    }

    /** 3500.00 shown as "3,500"; cents only when there are any. */
    public String priceDisplay() {
        DecimalFormat format = new DecimalFormat(monthlyPrice.stripTrailingZeros().scale() > 0 ? "#,##0.00" : "#,##0",
                DecimalFormatSymbols.getInstance(Locale.ROOT));
        return format.format(monthlyPrice);
    }

    /** "1-week free trial", "2-week free trial", "1-month free trial", or "10-day free trial". */
    public String trialLabel() {
        if (trialDays <= 0) {
            return null;
        }
        if (trialDays % 30 == 0) {
            return (trialDays / 30) + "-month free trial";
        }
        if (trialDays % 7 == 0) {
            return (trialDays / 7) + "-week free trial";
        }
        return trialDays + "-day free trial";
    }

    /** The trial in words for sentences: "1 week", "2 weeks", "1 month". */
    public String trialWords() {
        if (trialDays % 30 == 0) {
            int n = trialDays / 30;
            return n + (n == 1 ? " month" : " months");
        }
        if (trialDays % 7 == 0) {
            int n = trialDays / 7;
            return n + (n == 1 ? " week" : " weeks");
        }
        return trialDays + (trialDays == 1 ? " day" : " days");
    }

    public String highlightsText() {
        return String.join("\n", highlights);
    }
}
