package ai.zamu.conversations;

import java.util.Locale;

/** Inbox filters (spec: Inbox screen: needs attention, handed over, bot, closed). */
public enum InboxFilter {
    OPEN("All open"),
    ATTENTION("Needs attention"),
    HANDED_OVER("With staff"),
    BOT("With Zamu"),
    CLOSED("Closed");

    private final String label;

    InboxFilter(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public String param() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    public static InboxFilter fromParam(String value) {
        if (value == null || value.isBlank()) {
            return OPEN;
        }
        for (InboxFilter f : values()) {
            if (f.param().equals(value)) {
                return f;
            }
        }
        return OPEN;
    }
}
