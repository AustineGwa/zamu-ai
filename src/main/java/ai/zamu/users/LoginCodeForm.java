package ai.zamu.users;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** The 6-digit code from the login email, or from an authenticator app. */
record LoginCodeForm(
        @NotBlank(message = "Enter the 6-digit code")
        @Pattern(regexp = "\\s*\\d{3}\\s*\\d{3}\\s*", message = "The code is 6 digits") String code) {
}
