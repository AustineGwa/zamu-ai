package ai.zamu.users;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;

import ai.zamu.shared.security.ZamuRole;
import ai.zamu.support.AbstractMySqlIT;
import ai.zamu.support.TenantFixture;
import ai.zamu.tenants.CreateTenantRequest;
import ai.zamu.tenants.TenantPlan;
import ai.zamu.tenants.TenantService;
import ai.zamu.tenants.TenantView;
import ai.zamu.shared.ids.TenantId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserServiceIT extends AbstractMySqlIT {

    @Autowired
    UserService users;

    @Autowired
    TenantService tenants;

    TenantFixture fx;

    @BeforeEach
    void fixture() {
        fx = TenantFixture.create(tenants, users);
    }

    @Test
    void ownersCannotGoOverThePlanLoginLimit() {
        TenantView starter = tenants.create(new CreateTenantRequest("Chai Salon Ltd", "Chai", TenantPlan.STARTER));
        TenantId id = TenantId.of(starter.id());
        users.addTenantUser(id, new CreateUserRequest("One", "one@chai.test", ZamuRole.OWNER), true);
        users.addTenantUser(id, new CreateUserRequest("Two", "two@chai.test", ZamuRole.STAFF), true);

        assertThatThrownBy(() -> users.addTenantUser(id, new CreateUserRequest("Three", "three@chai.test", ZamuRole.STAFF), true))
                .isInstanceOf(StaffLimitReachedException.class)
                .hasMessageContaining("2 logins");

        // Zamu operators may go over the cap in the setup wizard (ASSUMPTION).
        assertThat(users.addTenantUser(id, new CreateUserRequest("Three", "three@chai.test", ZamuRole.STAFF), false)).isNotNull();
    }

    @Test
    void emailsAreUniqueAcrossTenants() {
        assertThatThrownBy(() -> users.addTenantUser(fx.idB(),
                new CreateUserRequest("Copy", "OWNER@afya.test", ZamuRole.STAFF), false))
                .isInstanceOf(DuplicateEmailException.class);
    }

    @Test
    void tenantUsersCanNeverBePlatformRoles() {
        assertThatThrownBy(() -> users.addTenantUser(fx.idA(),
                new CreateUserRequest("Sneaky", "sneaky@afya.test", ZamuRole.ADMIN), false))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void NFR_09_disable_userOfAnotherTenantIsNotFound() {
        assertThatThrownBy(() -> users.disable(fx.idB(), fx.staffA().id(), fx.ownerB().id()))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void ownersCannotDisableThemselves() {
        assertThatThrownBy(() -> users.disable(fx.idA(), fx.ownerA().id(), fx.ownerA().id()))
                .isInstanceOf(CannotDisableSelfException.class);
    }

    @Test
    void NFR_14_disablingIsAudited() {
        users.disable(fx.idA(), fx.staffA().id(), fx.ownerA().id());
        Integer count = jdbc.sql("SELECT COUNT(*) FROM audit_logs WHERE action = 'user.disabled'").query(Integer.class).single();
        assertThat(count).isEqualTo(1);
        assertThat(users.listForTenant(fx.idA())).filteredOn(u -> u.id().equals(fx.staffA().id()))
                .allMatch(u -> !u.active());
    }
}
