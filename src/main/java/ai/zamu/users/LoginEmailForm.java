package ai.zamu.users;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record LoginEmailForm(
        @NotBlank(message = "Enter your email address") @Email(message = "Enter a valid email address")
        @Size(max = 254) String email) {
}
