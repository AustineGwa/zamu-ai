package ai.zamu.conversations;

import java.util.Locale;

/** Delivery state (MSG-15). Outbound: queued, sent, delivered, read or failed; inbound: received. */
public enum MessageStatus {
    RECEIVED("Received", 0),
    QUEUED("Queued", 0),
    SENT("Sent", 1),
    DELIVERED("Delivered", 2),
    READ("Read", 3),
    FAILED("Failed", 9);

    private final String label;
    private final int rank;

    MessageStatus(String label, int rank) {
        this.label = label;
        this.rank = rank;
    }

    public String label() {
        return label;
    }

    /** Status updates only move forward (sent, delivered, read); failed always applies. */
    boolean canMoveTo(MessageStatus next) {
        return next == FAILED || next.rank > rank;
    }

    public String dbValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static MessageStatus fromDb(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }
}
