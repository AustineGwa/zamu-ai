package ai.zamu.pricing;

import java.util.List;
import java.util.Map;

/** Everything the pricing page shows, read from the database in one go and cached. */
public record PricingCatalog(List<PricingPlan> plans, List<ComparisonGroup> comparison, List<Faq> faqs) {

    public PricingCatalog {
        plans = List.copyOf(plans);
        comparison = List.copyOf(comparison);
        faqs = List.copyOf(faqs);
    }

    /** A heading in the comparison table, e.g. "Bookings and payments". */
    public record ComparisonGroup(String name, List<ComparisonRow> rows) {
    }

    /** One feature across the plans: plan code to "yes", "no" or a short text. */
    public record ComparisonRow(String label, Map<String, String> values) {

        public String value(String planCode) {
            return values.getOrDefault(planCode, "no");
        }

        public boolean included(String planCode) {
            return "yes".equalsIgnoreCase(value(planCode));
        }

        public boolean excluded(String planCode) {
            return "no".equalsIgnoreCase(value(planCode));
        }
    }

    public record Faq(String question, String answer) {
    }
}
