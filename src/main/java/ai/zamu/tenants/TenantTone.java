package ai.zamu.tenants;

import java.util.Locale;

/**
 * How Zamu speaks for the tenant. ASSUMPTION: the spec names a tone column without values; the intake
 * questionnaire (Operations Manual, Appendix A, item 12) asks "formal or friendly", so those are the values.
 */
public enum TenantTone {
    FRIENDLY("Friendly"),
    FORMAL("Formal");

    private final String label;

    TenantTone(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    String dbValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    static TenantTone fromDb(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }
}
