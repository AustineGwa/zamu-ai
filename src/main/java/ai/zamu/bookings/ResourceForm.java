package ai.zamu.bookings;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A staff member or room (spec: resources). */
public record ResourceForm(
        @NotBlank(message = "Enter a name") @Size(max = 120) String name,
        @Size(max = 120) String role,
        @Size(max = 255) String calendarId,
        boolean active) {

    static ResourceForm empty() {
        return new ResourceForm("", "", "", true);
    }
}
