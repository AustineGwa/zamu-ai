package ai.zamu.bookings;

import java.time.LocalDate;
import java.util.UUID;

/** A day the business is closed (spec: holidays). ASSUMPTION: only whole closed days in version 1. */
public record Holiday(UUID id, LocalDate date, boolean closed, String note) {
}
