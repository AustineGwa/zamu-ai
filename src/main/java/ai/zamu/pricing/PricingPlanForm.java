package ai.zamu.pricing;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Admin console: edit one plan on the public price list. Highlights are one per line. */
public record PricingPlanForm(
        @NotBlank(message = "Enter a name") @Size(max = 60) String name,
        @NotBlank(message = "Enter a tagline") @Size(max = 200) String tagline,
        @NotBlank(message = "Say who the plan is for") @Size(max = 200) String audience,
        @NotNull(message = "Enter the monthly price") @DecimalMin(value = "0.00", message = "The price can't be negative")
        @Digits(integer = 10, fraction = 2, message = "Up to two decimal places") BigDecimal monthlyPrice,
        @NotBlank(message = "Enter a price note, e.g. per month") @Size(max = 120) String priceNote,
        @Min(value = 0, message = "0 means no free trial") @Max(value = 365, message = "At most 365 days") int trialDays,
        @NotBlank(message = "Add at least one highlight") @Size(max = 2000) String highlights,
        @NotBlank(message = "Enter the button text") @Size(max = 60) String ctaLabel,
        @Size(max = 60) String badge,
        boolean featured,
        @Min(0) @Max(10_000) int sortOrder,
        boolean active) {

    static PricingPlanForm from(PricingPlan p) {
        return new PricingPlanForm(p.name(), p.tagline(), p.audience(), p.monthlyPrice(), p.priceNote(),
                p.trialDays(), p.highlightsText(), p.ctaLabel(), p.badge() == null ? "" : p.badge(), p.featured(),
                p.sortOrder(), p.active());
    }
}
