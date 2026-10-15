package ai.zamu.bookings;

/** A booking change that the tenant's rules don't allow, with a message for staff forms. */
public class BookingRuleException extends RuntimeException {

    public BookingRuleException(String message) {
        super(message);
    }
}
