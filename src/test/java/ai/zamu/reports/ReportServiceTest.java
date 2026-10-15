package ai.zamu.reports;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import ai.zamu.bookings.DayHours;

import static org.assertj.core.api.Assertions.assertThat;

/** Pure parts of the weekly report (REP-01, REP-02, REP-05). */
class ReportServiceTest {

    static final ZoneId NAIROBI = ZoneId.of("Africa/Nairobi");

    static List<DayHours> weekdays8to18() {
        List<DayHours> week = new ArrayList<>();
        for (DayOfWeek d : DayOfWeek.values()) {
            week.add(d == DayOfWeek.SUNDAY ? DayHours.closed(d)
                    : new DayHours(d, LocalTime.of(8, 0), LocalTime.of(18, 0), LocalTime.of(8, 0), LocalTime.of(18, 0)));
        }
        return week;
    }

    @Test
    void REP_01_afterHoursIsOutsideThatDaysOpeningHoursInTheBusinessZone() {
        List<DayHours> week = weekdays8to18();
        // Wednesday 14 October 2026; Nairobi is UTC+3.
        assertThat(ReportService.afterHours(Instant.parse("2026-10-14T04:59:00Z"), NAIROBI, week)).isTrue();  // 07:59
        assertThat(ReportService.afterHours(Instant.parse("2026-10-14T05:00:00Z"), NAIROBI, week)).isFalse(); // 08:00
        assertThat(ReportService.afterHours(Instant.parse("2026-10-14T14:59:00Z"), NAIROBI, week)).isFalse(); // 17:59
        assertThat(ReportService.afterHours(Instant.parse("2026-10-14T15:00:00Z"), NAIROBI, week)).isTrue();  // 18:00
        assertThat(ReportService.afterHours(Instant.parse("2026-10-18T09:00:00Z"), NAIROBI, week)).isTrue();  // Sunday
    }

    @Test
    void REP_01_medianFirstReplyMinutes() {
        assertThat(ReportService.median(List.of())).isNull();
        assertThat(ReportService.median(List.of(9L, 1L, 4L))).isEqualTo(4);
        assertThat(ReportService.median(List.of(10L, 4L))).isEqualTo(7);
        assertThat(ReportService.median(List.of(1L, 2L))).isEqualTo(2); // 1.5 rounds half up
    }

    @Test
    void REP_05_csvCellsAreQuotedAndCannotRunAsSpreadsheetFormulas() {
        assertThat(ReportService.csvCell("plain")).isEqualTo("plain");
        assertThat(ReportService.csvCell("a, b")).isEqualTo("\"a, b\"");
        assertThat(ReportService.csvCell("say \"hi\"")).isEqualTo("\"say \"\"hi\"\"\"");
        assertThat(ReportService.csvCell("=HYPERLINK(\"x\")")).startsWith("\"'=HYPERLINK");
        assertThat(ReportService.csvCell("+254712")).isEqualTo("'+254712");
        assertThat(ReportService.csvCell("-1")).isEqualTo("'-1");
        assertThat(ReportService.csvCell("@SUM")).isEqualTo("'@SUM");
        assertThat(ReportService.csvCell(null)).isEmpty();
    }
}
