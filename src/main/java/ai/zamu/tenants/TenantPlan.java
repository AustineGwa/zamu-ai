package ai.zamu.tenants;

import java.util.Locale;

/**
 * Plans and limits (Product and Operations Manual: Plans and limits). Fees are the launch prices still being
 * tested; conversations and staff logins are the plan caps.
 */
public enum TenantPlan {
    STARTER("Starter", 7_500, 500, 2),
    GROWTH("Growth", 15_000, 1_500, 5),
    BUSINESS("Business", 30_000, 4_000, 15);

    private final String label;
    private final int monthlyFeeKes;
    private final int conversationsPerMonth;
    private final int staffLogins;

    TenantPlan(String label, int monthlyFeeKes, int conversationsPerMonth, int staffLogins) {
        this.label = label;
        this.monthlyFeeKes = monthlyFeeKes;
        this.conversationsPerMonth = conversationsPerMonth;
        this.staffLogins = staffLogins;
    }

    public String label() {
        return label;
    }

    public int monthlyFeeKes() {
        return monthlyFeeKes;
    }

    public int conversationsPerMonth() {
        return conversationsPerMonth;
    }

    public int staffLogins() {
        return staffLogins;
    }

    String dbValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    static TenantPlan fromDb(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }
}
