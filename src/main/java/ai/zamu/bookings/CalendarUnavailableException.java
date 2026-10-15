package ai.zamu.bookings;

/**
 * Availability can't be checked against the tenant's calendar right now. Zamu never books blind, so callers hand
 * over to staff instead (BK-02).
 */
public class CalendarUnavailableException extends RuntimeException {

    public CalendarUnavailableException(String message) {
        super(message);
    }
}
