package ai.zamu.whatsapp;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Setup wizard, WhatsApp step: manual connection with IDs and a system-user access token from Meta Business
 * Manager. Embedded Signup is an open question in the spec. A blank token keeps the saved one.
 */
public record WhatsAppConnectForm(
        @NotBlank(message = "Enter the WhatsApp Business Account ID") @Pattern(regexp = "\\d{5,32}", message = "Digits only") String wabaId,
        @NotBlank(message = "Enter the phone number ID") @Pattern(regexp = "\\d{5,32}", message = "Digits only") String phoneNumberId,
        @NotBlank(message = "Enter the number customers will message") @Size(max = 32) String displayPhone,
        @Size(max = 1000) String accessToken) {

    static WhatsAppConnectForm empty() {
        return new WhatsAppConnectForm("", "", "", "");
    }
}
