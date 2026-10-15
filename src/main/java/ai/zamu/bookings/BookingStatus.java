package ai.zamu.bookings;

import java.util.Locale;
import java.util.Set;

/** Spec: Booking states. held → pending_payment → confirmed → completed or no_show; any → cancelled. */
public enum BookingStatus {
    HELD("Held"),
    PENDING_PAYMENT("Awaiting deposit"),
    CONFIRMED("Confirmed"),
    CANCELLED("Cancelled"),
    COMPLETED("Completed"),
    NO_SHOW("No-show");

    /** Statuses that occupy the slot. */
    static final Set<BookingStatus> BLOCKING = Set.of(HELD, PENDING_PAYMENT, CONFIRMED);

    private final String label;

    BookingStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean open() {
        return BLOCKING.contains(this);
    }

    String dbValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    static BookingStatus fromDb(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }
}
