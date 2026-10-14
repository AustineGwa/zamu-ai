package ai.zamu.tenants;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Admin console: create a tenant (first step of onboarding). */
public record CreateTenantRequest(
        @NotBlank(message = "Enter the registered business name") @Size(max = 200) String name,
        @NotBlank(message = "Enter the name customers use") @Size(max = 200) String displayName,
        @NotNull(message = "Choose a plan") TenantPlan plan) {

    static CreateTenantRequest empty() {
        return new CreateTenantRequest("", "", TenantPlan.STARTER);
    }
}
