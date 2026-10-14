package ai.zamu.conversations;

import java.util.Locale;

/** Where a conversation happens. Voice joins in a later milestone (docs/voice-channel-proposal.md). */
public enum Channel {
    WHATSAPP("WhatsApp"),
    VOICE("Phone call");

    private final String label;

    Channel(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public String dbValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Channel fromDb(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }
}
