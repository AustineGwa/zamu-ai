package ai.zamu.tenants;

import java.util.Locale;

/** Tenant lifecycle (spec: Data model, tenants.status). */
public enum TenantStatus {
    ONBOARDING("Onboarding"),
    LIVE("Live"),
    STAFF_ONLY("Staff-only"),
    PAUSED("Paused"),
    OFFBOARDED("Offboarded");

    private final String label;

    TenantStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    String dbValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    static TenantStatus fromDb(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }
}
