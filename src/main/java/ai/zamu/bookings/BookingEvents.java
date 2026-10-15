package ai.zamu.bookings;

/**
 * What happened to a booking, for the packages that tell the customer (conversations sends the WhatsApp message)
 * and, later, take payments. Published inside the booking's transaction; listeners run in it.
 */
public final class BookingEvents {

    private BookingEvents() {
    }

    /** Confirmed (BK-07). {@code notifyCustomer} is false when Zamu already said so in the chat. */
    public record Confirmed(Booking booking, boolean notifyCustomer) {
    }

    /** Cancelled by staff, the calendar or the system (hold expired); customers who cancel are told in the chat. */
    public record Cancelled(Booking booking, String by, boolean notifyCustomer) {
    }

    /** Moved to a new time (BK-09, BK-10). */
    public record Moved(Booking booking, String by, boolean notifyCustomer) {
    }

    /** BK-11: time to send the reminder template. {@code kind} is "24h" or "2h". */
    public record ReminderDue(Booking booking, String kind) {
    }

    /** A booking whose service needs a deposit is waiting for payment (PAY flow, step 1). */
    public record DepositNeeded(Booking booking) {
    }
}
