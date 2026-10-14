package ai.zamu.handovers;

import java.util.Locale;

/**
 * Why a conversation went to staff (spec: handovers.reason). STAFF_ONLY and STAFF_INITIATED are additions
 * (ASSUMPTION): the business switched on staff-only mode, or a staff member replied to a chat Zamu was handling.
 */
public enum HandoverReason {
    REQUESTED("Customer asked for a person"),
    SENSITIVE("Sensitive or emergency topic"),
    LOW_CONFIDENCE("Zamu wasn't sure"),
    PAYMENT_ISSUE("Payment problem"),
    ERROR("Something went wrong"),
    STAFF_ONLY("Staff-only mode"),
    STAFF_INITIATED("Staff took over");

    private final String label;

    HandoverReason(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    String dbValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    static HandoverReason fromDb(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }
}
