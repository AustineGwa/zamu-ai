package ai.zamu.shared.security;

import java.util.Locale;

/** Dashboard and console roles (spec: Users, roles and permissions). Customers never log in. */
public enum ZamuRole {
    STAFF(false),
    OWNER(false),
    OPERATOR(true),
    ADMIN(true);

    private final boolean platformStaff;

    ZamuRole(boolean platformStaff) {
        this.platformStaff = platformStaff;
    }

    /** Zamu team members work across tenants, have no tenant_id, and need a second factor (NFR-08). */
    public boolean isPlatformStaff() {
        return platformStaff;
    }

    /** Spring Security authority name, e.g. ROLE_OWNER. */
    public String authority() {
        return "ROLE_" + name();
    }

    /** Value stored in the database (VARCHAR(32), checked in code). */
    public String dbValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static ZamuRole fromDb(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }

    public String label() {
        return switch (this) {
            case STAFF -> "Staff";
            case OWNER -> "Owner";
            case OPERATOR -> "Zamu operator";
            case ADMIN -> "Zamu admin";
        };
    }
}
