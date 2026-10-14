package ai.zamu.users;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import ai.zamu.shared.ids.TenantId;
import ai.zamu.shared.security.ZamuRole;
import ai.zamu.tenants.CreateTenantRequest;
import ai.zamu.tenants.TenantPlan;
import ai.zamu.tenants.TenantService;
import ai.zamu.tenants.TenantView;

/**
 * Local "h2" profile only: creates a demo tenant with an owner and a staff login, so every dashboard page can
 * be tried straight away. Owners and staff don't need an authenticator app; the Zamu admin does.
 */
@Component
@Profile("h2")
@ConditionalOnProperty(name = "zamu.local-demo.enabled", havingValue = "true")
@Order(100) // after AdminBootstrap
class LocalDemoData implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LocalDemoData.class);

    private final TenantService tenants;
    private final UserService users;

    LocalDemoData(TenantService tenants, UserService users) {
        this.tenants = tenants;
        this.users = users;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!tenants.listAll().isEmpty()) {
            return;
        }
        TenantView demo = tenants.create(new CreateTenantRequest("Demo Clinic Ltd", "Demo Clinic", TenantPlan.GROWTH));
        TenantId id = TenantId.of(demo.id());
        users.addTenantUser(id, new CreateUserRequest("Demo Owner", "owner@demo.local", ZamuRole.OWNER), false);
        users.addTenantUser(id, new CreateUserRequest("Demo Staff", "staff@demo.local", ZamuRole.STAFF), false);
        log.info("Local demo data: tenant 'Demo Clinic' with owner@demo.local and staff@demo.local");
    }
}
