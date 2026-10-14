package ai.zamu.leads;

import java.util.Locale;

/** Where a sign-up is in the sales conversation. */
public enum LeadStatus {
    NEW("New"),
    CONTACTED("Contacted"),
    WON("Became a client"),
    LOST("Not going ahead");

    private final String label;

    LeadStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    String dbValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    static LeadStatus fromDb(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }
}
