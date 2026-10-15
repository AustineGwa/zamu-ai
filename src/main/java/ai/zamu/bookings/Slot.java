package ai.zamu.bookings;

import java.time.Instant;
import java.util.UUID;

/** A free time for a service, with the resource that would deliver it (null when the service has none). */
public record Slot(UUID serviceId, UUID resourceId, String resourceName, Instant startsAt, Instant endsAt) {
}
