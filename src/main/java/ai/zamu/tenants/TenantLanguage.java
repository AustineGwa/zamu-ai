package ai.zamu.tenants;

import java.util.Locale;

/** The tenant's first language for replies when a customer's language is unclear (AI-02). */
public enum TenantLanguage {
    EN("English"),
    SW("Swahili");

    private final String label;

    TenantLanguage(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    String dbValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    static TenantLanguage fromDb(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }
}
