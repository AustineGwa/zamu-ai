package ai.zamu.shared.ids;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * The tenant a piece of work belongs to (NFR-09).
 *
 * <p>Every repository method on a tenant-owned table takes a {@code TenantId}. A {@code TenantId} must only
 * be obtained from the authenticated session ({@code CurrentTenantResolver}), from a job
 * ({@code Job.tenantId()}), or from {@code AdminTenantScope} after the operator/admin role check. Never
 * build one from a request parameter or path variable in a tenant-facing route.
 */
public record TenantId(UUID value) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public TenantId {
        Objects.requireNonNull(value, "tenant id");
    }

    public static TenantId of(UUID value) {
        return new TenantId(value);
    }

    public byte[] bytes() {
        return Uuids.toBytes(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
