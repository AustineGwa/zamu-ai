package ai.zamu.bookings;

/** A calendar call failed. {@code revoked}: the tenant's authorisation is gone and must be reconnected (BK-01). */
class CalendarApiException extends RuntimeException {

    private final boolean revoked;

    CalendarApiException(String message, boolean revoked) {
        super(message);
        this.revoked = revoked;
    }

    boolean revoked() {
        return revoked;
    }
}
