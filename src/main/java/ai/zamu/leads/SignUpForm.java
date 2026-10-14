package ai.zamu.leads;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The public sign-up form. {@code website} is a honeypot: hidden from people, often filled in by spam bots.
 * The phone number is checked and normalised by {@link LeadService}.
 */
public record SignUpForm(
        @NotBlank(message = "Enter your business name") @Size(max = 200) String businessName,
        @NotBlank(message = "Enter your name") @Size(max = 120) String contactName,
        @NotBlank(message = "Enter a phone number we can call") @Size(max = 32) String phone,
        @Email(message = "Enter a valid email address, or leave it empty") @Size(max = 254) String email,
        @NotNull(message = "Choose the kind of business") BusinessType businessType,
        @Size(max = 1000, message = "Keep this under 1,000 characters") String message,
        @AssertTrue(message = "Tick the box so we can contact you") boolean consent,
        String website) {

    static SignUpForm empty() {
        return new SignUpForm("", "", "", "", null, "", false, "");
    }
}
