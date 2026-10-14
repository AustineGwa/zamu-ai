package ai.zamu.conversations;

import java.time.Instant;
import java.util.UUID;

/** A customers row. Identified by phone number, which works for WhatsApp and voice alike. */
record Customer(UUID id, UUID tenantId, String phone, String name, String language, boolean optedInMarketing,
        Instant optedOutAt, boolean blocked, Instant deletedAt, Instant createdAt, Instant updatedAt) {
}
