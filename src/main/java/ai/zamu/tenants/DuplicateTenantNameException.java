package ai.zamu.tenants;

public class DuplicateTenantNameException extends RuntimeException {

    public DuplicateTenantNameException() {
        super("A tenant with this registered name already exists");
    }
}
