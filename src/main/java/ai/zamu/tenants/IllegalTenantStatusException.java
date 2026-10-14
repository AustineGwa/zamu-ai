package ai.zamu.tenants;

public class IllegalTenantStatusException extends RuntimeException {

    public IllegalTenantStatusException(TenantStatus from, TenantStatus to) {
        super("Staff-only mode can only be switched while the business is live (now " + from.label() + ", wanted "
                + to.label() + ")");
    }
}
