package ai.zamu.bookings;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Setup wizard, hours step: seven rows, Monday first, times as HH:mm (from input type=time). */
public record HoursForm(@NotNull @Size(min = 7, max = 7) @Valid List<Row> days) {

    private static final String TIME = "^$|^([01]\\d|2[0-3]):[0-5]\\d$";

    public record Row(
            @Pattern(regexp = TIME, message = "Use a time like 08:00") String opensAt,
            @Pattern(regexp = TIME, message = "Use a time like 17:00") String closesAt,
            @Pattern(regexp = TIME, message = "Use a time like 08:00") String handoverOpensAt,
            @Pattern(regexp = TIME, message = "Use a time like 17:00") String handoverClosesAt) {
    }

    static HoursForm from(List<DayHours> week) {
        List<Row> rows = new ArrayList<>();
        for (DayHours d : week) {
            rows.add(new Row(text(d.opensAt()), text(d.closesAt()), text(d.handoverOpensAt()), text(d.handoverClosesAt())));
        }
        return new HoursForm(rows);
    }

    List<DayHours> toWeek() {
        List<DayHours> week = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            Row r = days.get(i);
            week.add(new DayHours(DayOfWeek.of(i + 1), time(r.opensAt()), time(r.closesAt()),
                    time(r.handoverOpensAt()), time(r.handoverClosesAt())));
        }
        return week;
    }

    private static String text(LocalTime t) {
        return t == null ? "" : t.toString().substring(0, 5);
    }

    private static LocalTime time(String s) {
        return s == null || s.isBlank() ? null : LocalTime.parse(s);
    }
}
