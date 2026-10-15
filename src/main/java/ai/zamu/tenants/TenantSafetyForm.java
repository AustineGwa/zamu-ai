package ai.zamu.tenants;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Setup wizard, tone step, safety part: AI-03 confidence threshold (default 0.60), AI-04 emergency number used in
 * the emergency reply, HO-01 topics that always go to a person (one per line).
 */
public record TenantSafetyForm(
        @NotNull(message = "Enter a threshold")
        @DecimalMin(value = "0.30", message = "Use 0.30 or more") @DecimalMax(value = "0.95", message = "Use 0.95 or less")
        BigDecimal confidenceThreshold,
        @Size(max = 100, message = "Keep it under 100 characters") String emergencyNumber,
        @Size(max = 1000, message = "Keep it under 1,000 characters") String handoverTopics) {

    static TenantSafetyForm from(TenantView tenant) {
        return new TenantSafetyForm(tenant.confidenceThreshold(), tenant.emergencyNumber(), tenant.handoverTopics());
    }
}
