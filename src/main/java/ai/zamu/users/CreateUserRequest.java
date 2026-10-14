package ai.zamu.users;

import ai.zamu.shared.security.ZamuRole;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Add a dashboard user to a tenant (owner: /app/users; operator: setup wizard staff step). */
public record CreateUserRequest(
        @NotBlank(message = "Enter their name") @Size(max = 120) String name,
        @NotBlank(message = "Enter their email address") @Email(message = "Enter a valid email address")
        @Size(max = 254) String email,
        @NotNull(message = "Choose a role") ZamuRole role) {

    static CreateUserRequest empty() {
        return new CreateUserRequest("", "", ZamuRole.STAFF);
    }
}
