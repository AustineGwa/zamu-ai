package ai.zamu.knowledge;

import java.util.Locale;

/** Knowledge base entry state. Only approved entries are ever used in replies (AI-01). */
public enum KbEntryStatus {
    DRAFT("Draft"),
    APPROVED("Approved"),
    ARCHIVED("Archived");

    private final String label;

    KbEntryStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    String dbValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    static KbEntryStatus fromDb(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }
}
