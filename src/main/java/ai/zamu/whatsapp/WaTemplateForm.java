package ai.zamu.whatsapp;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** A new template (admin console). Names follow Meta's rule: lower case letters, digits and underscores. */
public record WaTemplateForm(
        @NotBlank(message = "Enter a name") @Size(max = 512) @Pattern(regexp = "[a-z0-9_]+", message = "Lower case letters, digits and underscores only") String name,
        @NotBlank(message = "Choose a category") @Pattern(regexp = "utility|marketing", message = "Choose utility or marketing") String category,
        @NotBlank(message = "Choose a language") @Pattern(regexp = "en|sw", message = "Choose English or Swahili") String language,
        @NotBlank(message = "Enter the message") @Size(max = 1024, message = "Keep it under 1024 characters") String body) {

    static WaTemplateForm empty() {
        return new WaTemplateForm("", "utility", "en", "");
    }
}
