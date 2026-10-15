package ai.zamu.bookings;

import java.util.UUID;

import ai.zamu.shared.ids.TenantId;

/**
 * Finds or creates the customer for a staff booking. Implemented by the conversations package, which owns
 * customers; declared here so bookings never depends on conversations.
 */
public interface CustomerDirectory {

    /** @param phone international digits, e.g. 254712345678 */
    UUID findOrCreate(TenantId tenantId, String phone, String name);
}
