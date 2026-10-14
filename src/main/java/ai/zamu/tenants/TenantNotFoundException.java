package ai.zamu.tenants;

import ai.zamu.shared.web.NotFoundException;

public class TenantNotFoundException extends NotFoundException {

    public TenantNotFoundException() {
        super("Tenant not found");
    }
}
