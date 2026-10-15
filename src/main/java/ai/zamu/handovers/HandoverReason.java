package ai.zamu.handovers;

import java.util.Locale;

/**
 * Why a conversation went to staff (spec: handovers.reason). Additions (ASSUMPTION): STAFF_ONLY (staff-only mode),
 * STAFF_INITIATED (a staff member replied to a chat Zamu was handling), TOPIC (a topic the business always handles
 * in person, HO-01), BOOKING (booking or payment requests until Milestones 5 and 6), LIMIT (too many messages,
 * MSG-16).
 */
public enum HandoverReason {
    REQUESTED("Customer asked for a person"),
    SENSITIVE("Sensitive or emergency topic"),
    LOW_CONFIDENCE("Zamu wasn't sure"),
    PAYMENT_ISSUE("Payment problem"),
    ERROR("Something went wrong"),
    STAFF_ONLY("Staff-only mode"),
    STAFF_INITIATED("Staff took over"),
    TOPIC("Topic the business handles in person"),
    BOOKING("Wants to book or pay"),
    LIMIT("Too many messages");

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
