package ai.zamu.tenants;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Setup wizard step 1, business profile. */
public record TenantProfileForm(
        @NotBlank(message = "Enter the registered business name") @Size(max = 200) String name,
        @NotBlank(message = "Enter the name customers use") @Size(max = 200) String displayName,
        @NotNull(message = "Choose a plan") TenantPlan plan,
        @NotNull(message = "Enter the monthly conversation limit")
        @Min(value = 1, message = "Must be at least 1")
        @Max(value = 1_000_000, message = "That limit is too large") Integer conversationLimit) {

    static TenantProfileForm from(TenantView tenant) {
        return new TenantProfileForm(tenant.name(), tenant.displayName(), tenant.plan(), tenant.conversationLimit());
    }
}
