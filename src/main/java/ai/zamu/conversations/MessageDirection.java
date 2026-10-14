package ai.zamu.conversations;

import java.util.Locale;

/** Inbound from the customer, or outbound to them. */
public enum MessageDirection {
    IN("In"),
    OUT("Out");

    private final String label;

    MessageDirection(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public String dbValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static MessageDirection fromDb(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }
}
