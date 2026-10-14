package ai.zamu.users;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import ai.zamu.shared.security.ZamuRole;
import ai.zamu.support.AbstractMySqlIT;
import ai.zamu.support.TenantFixture;
import ai.zamu.tenants.TenantService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Tenant isolation for every tenant-scoped UserRepository method (NFR-09). */
class UserRepositoryIT extends AbstractMySqlIT {

    @Autowired
    UserRepository repository;

    @Autowired
    TenantService tenants;

    @Autowired
    UserService users;

    TenantFixture fx;

    @BeforeEach
    void fixture() {
        fx = TenantFixture.create(tenants, users);
    }

    @Test
    void NFR_09_findAllInTenant_returnsOnlyThatTenantsUsers() {
        assertThat(repository.findAllInTenant(fx.idA())).extracting(User::email)
                .containsExactlyInAnyOrder("owner@afya.test", "staff@afya.test");
    }

    @Test
    void NFR_09_findInTenant_cannotReadAnotherTenantsUser() {
        assertThat(repository.findInTenant(fx.idA(), fx.ownerA().id())).isPresent();
        assertThat(repository.findInTenant(fx.idB(), fx.ownerA().id())).isEmpty();
    }

    @Test
    void NFR_09_disableInTenant_cannotDisableAnotherTenantsUser() {
        assertThat(repository.disableInTenant(fx.idB(), fx.staffA().id(), clock.instant())).isZero();
        assertThat(repository.findInTenant(fx.idA(), fx.staffA().id())).get().extracting(User::isActive).isEqualTo(true);
    }

    @Test
    void NFR_09_countActiveInTenant_countsOnlyThatTenant() {
        repository.disableInTenant(fx.idA(), fx.staffA().id(), clock.instant());
        assertThat(repository.countActiveInTenant(fx.idA())).isEqualTo(1);
        assertThat(repository.countActiveInTenant(fx.idB())).isEqualTo(2);
    }

    @Test
    void NFR_09_insertTenantUser_refusesAUserForAnotherTenant() {
        User user = User.newUser(UUID.randomUUID(), fx.idA().value(), "x@afya.test", "X", ZamuRole.STAFF, clock.instant());
        assertThatThrownBy(() -> repository.insertTenantUser(fx.idB(), user)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void emailIsStoredLowerCaseAndUniqueAcrossTenants() {
        assertThat(repository.existsByEmail("owner@afya.test")).isTrue();
        assertThat(repository.findActiveByEmail("owner@afya.test")).get().extracting(User::tenantId)
                .isEqualTo(fx.idA().value());
    }
}
