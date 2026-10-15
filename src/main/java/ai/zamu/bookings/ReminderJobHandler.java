package ai.zamu.bookings;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import ai.zamu.shared.jobs.Job;
import ai.zamu.shared.jobs.JobHandler;

/** Job {@code booking_reminder} (BK-11): payload {bookingId, kind, startsAt}. Skipped if the booking changed. */
@Component
class ReminderJobHandler implements JobHandler {

    private final BookingService bookings;
    private final ObjectMapper objectMapper;

    ReminderJobHandler(BookingService bookings, ObjectMapper objectMapper) {
        this.bookings = bookings;
        this.objectMapper = objectMapper;
    }

    @Override
    public String type() {
        return BookingService.REMINDER_JOB;
    }

    @Override
    public void handle(Job job) {
        JsonNode payload = objectMapper.readTree(job.payloadJson());
        bookings.remind(job.tenantId(), UUID.fromString(payload.path("bookingId").asString("")),
                payload.path("kind").asString("24h"), Instant.parse(payload.path("startsAt").asString("")));
    }
}
