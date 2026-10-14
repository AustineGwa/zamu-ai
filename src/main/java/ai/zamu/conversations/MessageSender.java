package ai.zamu.conversations;

import java.util.Locale;

/** Who wrote a message. */
public enum MessageSender {
    CUSTOMER("Customer"),
    BOT("Zamu"),
    STAFF("Staff"),
    SYSTEM("System");

    private final String label;

    MessageSender(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public String dbValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static MessageSender fromDb(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }
}
