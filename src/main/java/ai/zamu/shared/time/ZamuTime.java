package ai.zamu.shared.time;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/**
 * Time rules (NFR-18): store UTC in DATETIME(3), display and schedule in Africa/Nairobi.
 * The JDBC connection is forced to UTC (see application.yml), and DATETIME values are read and written as
 * {@link LocalDateTime} in UTC so no driver time-zone conversion can creep in.
 */
public final class ZamuTime {

    public static final ZoneId NAIROBI = ZoneId.of("Africa/Nairobi");

    private static final DateTimeFormatter DISPLAY =
            DateTimeFormatter.ofPattern("EEE d MMM yyyy, HH:mm", Locale.ENGLISH).withZone(NAIROBI);

    private ZamuTime() {
    }

    /** Converts an instant to the UTC LocalDateTime written to a DATETIME(3) column. */
    public static LocalDateTime toDb(Instant instant) {
        if (instant == null) {
            return null;
        }
        return LocalDateTime.ofInstant(instant.truncatedTo(ChronoUnit.MILLIS), ZoneOffset.UTC);
    }

    /** Reads a nullable DATETIME(3) column holding UTC as an instant. */
    public static Instant read(ResultSet rs, String column) throws SQLException {
        LocalDateTime value = rs.getObject(column, LocalDateTime.class);
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    /** Formats an instant for people in Kenya, e.g. "Wed 14 Oct 2026, 08:30". */
    public static String display(Instant instant) {
        return instant == null ? "" : DISPLAY.format(instant);
    }
}
