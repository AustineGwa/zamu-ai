package ai.zamu.bookings;

import java.util.UUID;

/** A staff member or room that delivers services (spec: resources). */
public record BookingResource(UUID id, String name, String role, String calendarId, boolean active) {
}
