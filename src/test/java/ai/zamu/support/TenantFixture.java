package ai.zamu.support;

import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import ai.zamu.shared.ids.TenantId;
import ai.zamu.shared.security.ZamuPrincipal;
import ai.zamu.shared.security.ZamuRole;
import ai.zamu.tenants.CreateTenantRequest;
import ai.zamu.tenants.TenantPlan;
import ai.zamu.tenants.TenantService;
import ai.zamu.tenants.TenantView;
import ai.zamu.users.CreateUserRequest;
import ai.zamu.users.UserService;
import ai.zamu.users.UserView;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

/**
 * Cross-tenant test harness (NFR-09): two tenants, A and B, each with an owner and a staff member. Tests act as
 * a user of A and check they can't see or change anything of B's.
 */
public record TenantFixture(TenantView tenantA, UserView ownerA, UserView staffA,
        TenantView tenantB, UserView ownerB, UserView staffB) {

    public static TenantFixture create(TenantService tenants, UserService users) {
        TenantView a = tenants.create(new CreateTenantRequest("Afya Clinic Ltd", "Afya Clinic", TenantPlan.GROWTH));
        TenantView b = tenants.create(new CreateTenantRequest("Baraka Salon Ltd", "Baraka Salon", TenantPlan.GROWTH));
        TenantId ta = TenantId.of(a.id());
        TenantId tb = TenantId.of(b.id());
        return new TenantFixture(
                a,
                users.addTenantUser(ta, new CreateUserRequest("Amina Owner", "owner@afya.test", ZamuRole.OWNER), false),
                users.addTenantUser(ta, new CreateUserRequest("Juma Staff", "staff@afya.test", ZamuRole.STAFF), false),
                b,
                users.addTenantUser(tb, new CreateUserRequest("Beatrice Owner", "owner@baraka.test", ZamuRole.OWNER), false),
                users.addTenantUser(tb, new CreateUserRequest("Brian Staff", "staff@baraka.test", ZamuRole.STAFF), false));
    }

    public TenantId idA() {
        return TenantId.of(tenantA.id());
    }

    public TenantId idB() {
        return TenantId.of(tenantB.id());
    }

    public static ZamuPrincipal principal(UserView user, TenantView tenant) {
        return new ZamuPrincipal(user.id(), TenantId.of(tenant.id()), user.role(), user.email(), user.name());
    }

    /** MockMvc: act as this tenant user. */
    public static RequestPostProcessor as(UserView user, TenantView tenant) {
        return as(principal(user, tenant));
    }

    /** MockMvc: act as any principal, including a Zamu operator or admin. */
    public static RequestPostProcessor as(ZamuPrincipal principal) {
        return authentication(UsernamePasswordAuthenticationToken.authenticated(principal, null,
                List.of(new SimpleGrantedAuthority(principal.role().authority()))));
    }
}
