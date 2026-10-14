package ai.zamu.conversations;

import java.util.Locale;

/** What kind of message it is. */
public enum MessageType {
    TEXT("Text"),
    INTERACTIVE("Button or list reply"),
    TEMPLATE("Template"),
    MEDIA("Media");

    private final String label;

    MessageType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public String dbValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static MessageType fromDb(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }
}
