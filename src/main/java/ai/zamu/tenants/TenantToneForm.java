package ai.zamu.tenants;

import jakarta.validation.constraints.NotNull;

/** Setup wizard step, tone and language. */
public record TenantToneForm(
        @NotNull(message = "Choose a tone") TenantTone tone,
        @NotNull(message = "Choose a language") TenantLanguage defaultLanguage) {

    static TenantToneForm from(TenantView tenant) {
        return new TenantToneForm(tenant.tone(), tenant.defaultLanguage());
    }
}
