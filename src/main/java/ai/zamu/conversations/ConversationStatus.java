package ai.zamu.conversations;

import java.util.Locale;

/** Who is handling a conversation (spec: conversations.status). */
public enum ConversationStatus {
    BOT("Zamu"),
    HANDED_OVER("With staff"),
    CLOSED("Closed");

    private final String label;

    ConversationStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public String dbValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static ConversationStatus fromDb(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }
}
