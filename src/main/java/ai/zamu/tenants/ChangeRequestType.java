package ai.zamu.tenants;

import java.util.Locale;

/** What an owner asks the Zamu team to change (spec: change_requests). */
public enum ChangeRequestType {
    KB("Knowledge base answer"),
    HOURS("Hours or closed days"),
    SERVICE("Services or prices"),
    OTHER("Something else");

    private final String label;

    ChangeRequestType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    String dbValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    static ChangeRequestType fromDb(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }
}
