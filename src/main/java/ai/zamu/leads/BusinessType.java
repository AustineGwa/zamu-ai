package ai.zamu.leads;

import java.util.Locale;

/** The kinds of business Zamu is sold to first (Launch Plan: Who to sell to first). */
public enum BusinessType {
    CLINIC("Clinic or medical practice"),
    SALON("Salon, spa or barbershop"),
    SCHOOL("School or college"),
    OTHER("Something else");

    private final String label;

    BusinessType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    String dbValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    static BusinessType fromDb(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }
}
