package ai.zamu.tenants;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import ai.zamu.shared.ids.TenantId;
import ai.zamu.shared.ids.UuidV7;
import ai.zamu.shared.ids.Uuids;
import ai.zamu.support.AbstractMySqlIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantServiceIT extends AbstractMySqlIT {

    @Autowired
    TenantService tenants;

    @Autowired
    TenantRepository repository;

    @Test
    void createsATenantInOnboardingWithPlanDefaults() {
        TenantView created = tenants.create(new CreateTenantRequest("  Afya Clinic Ltd ", "Afya Clinic", TenantPlan.GROWTH));

        TenantView stored = tenants.get(TenantId.of(created.id()));
        assertThat(stored.name()).isEqualTo("Afya Clinic Ltd");
        assertThat(stored.status()).isEqualTo(TenantStatus.ONBOARDING);
        assertThat(stored.conversationLimit()).isEqualTo(1_500);
        assertThat(stored.timezone()).isEqualTo("Africa/Nairobi");
        assertThat(stored.confidenceThreshold()).isEqualByComparingTo(new BigDecimal("0.60"));
        assertThat(stored.createdAt()).isEqualTo(clock.instant());
    }

    @Test
    void NFR_14_tenantCreationIsAudited() {
        TenantView created = tenants.create(new CreateTenantRequest("Afya Clinic Ltd", "Afya Clinic", TenantPlan.STARTER));

        Integer audited = jdbc.sql("SELECT COUNT(*) FROM audit_logs WHERE action = 'tenant.created' AND entity_id = :id")
                .param("id", Uuids.toBytes(created.id()))
                .query(Integer.class).single();
        assertThat(audited).isEqualTo(1);
    }

    @Test
    void registeredNamesAreUnique() {
        tenants.create(new CreateTenantRequest("Afya Clinic Ltd", "Afya", TenantPlan.STARTER));
        assertThatThrownBy(() -> tenants.create(new CreateTenantRequest("Afya Clinic Ltd", "Other", TenantPlan.STARTER)))
                .isInstanceOf(DuplicateTenantNameException.class);
    }

    @Test
    void updatesProfileAndTone() {
        TenantView t = tenants.create(new CreateTenantRequest("Afya Clinic Ltd", "Afya", TenantPlan.STARTER));
        TenantId id = TenantId.of(t.id());

        tenants.updateProfile(id, new TenantProfileForm("Afya Clinic Ltd", "Afya Clinic Kilimani", TenantPlan.BUSINESS, 4_000));
        tenants.updateTone(id, new TenantToneForm(TenantTone.FORMAL, TenantLanguage.SW));

        TenantView after = tenants.get(id);
        assertThat(after.displayName()).isEqualTo("Afya Clinic Kilimani");
        assertThat(after.plan()).isEqualTo(TenantPlan.BUSINESS);
        assertThat(after.tone()).isEqualTo(TenantTone.FORMAL);
        assertThat(after.defaultLanguage()).isEqualTo(TenantLanguage.SW);
        assertThat(tenants.loginLimit(id)).isEqualTo(15);
    }

    @Test
    void NFR_09_updatesOnlyTouchTheGivenTenant() {
        TenantView a = tenants.create(new CreateTenantRequest("Afya Clinic Ltd", "Afya", TenantPlan.STARTER));
        TenantView b = tenants.create(new CreateTenantRequest("Baraka Salon Ltd", "Baraka", TenantPlan.STARTER));

        tenants.updateTone(TenantId.of(a.id()), new TenantToneForm(TenantTone.FORMAL, TenantLanguage.SW));

        assertThat(tenants.get(TenantId.of(b.id())).tone()).isEqualTo(TenantTone.FRIENDLY);
    }

    @Test
    void NFR_09_unknownTenantIsNotFound() {
        TenantId unknown = TenantId.of(UuidV7.next());
        assertThat(repository.findById(unknown)).isEmpty();
        assertThatThrownBy(() -> tenants.get(unknown)).isInstanceOf(TenantNotFoundException.class);
    }
}
