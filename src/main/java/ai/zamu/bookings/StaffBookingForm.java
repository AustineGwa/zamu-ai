package ai.zamu.bookings;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A booking staff make for a customer (BK-09). {@code slot} is "{resourceId|0}:{epochSeconds}" from the slot
 * picker. BK-14: name, phone and service only; notes are optional.
 */
public record StaffBookingForm(
        @NotBlank(message = "Enter the customer's phone number") String phone,
        @Size(max = 120) String name,
        @NotNull(message = "Choose a service") UUID serviceId,
        String date,
        @NotBlank(message = "Choose a time") String slot,
        @Size(max = 500) String notes) {

    static StaffBookingForm empty() {
        return new StaffBookingForm("", "", null, null, null, "");
    }
}
