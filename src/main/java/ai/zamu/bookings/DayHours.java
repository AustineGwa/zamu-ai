package ai.zamu.bookings;

import java.time.DayOfWeek;
import java.time.LocalTime;

/**
 * Opening and handover hours for one weekday, in the tenant's timezone. Null times mean closed, or no staff for
 * handovers, that day.
 */
public record DayHours(DayOfWeek day, LocalTime opensAt, LocalTime closesAt, LocalTime handoverOpensAt,
        LocalTime handoverClosesAt) {

    public static DayHours closed(DayOfWeek day) {
        return new DayHours(day, null, null, null, null);
    }

    public boolean handoverOpenAt(LocalTime time) {
        return handoverOpensAt != null && handoverClosesAt != null
                && !time.isBefore(handoverOpensAt) && time.isBefore(handoverClosesAt);
    }
}
