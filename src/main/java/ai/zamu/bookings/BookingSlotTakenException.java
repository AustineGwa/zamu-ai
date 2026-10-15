package ai.zamu.bookings;

/** The chosen time is no longer free (BK-05): offer other slots. */
public class BookingSlotTakenException extends RuntimeException {

    public BookingSlotTakenException() {
        super("That time is no longer free.");
    }
}
