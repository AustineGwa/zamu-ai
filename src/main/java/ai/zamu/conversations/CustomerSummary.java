package ai.zamu.conversations;

import java.time.Instant;
import java.util.UUID;

/** A customer as the privacy tools show them (NFR-12): enough to confirm it's the right person. */
public record CustomerSummary(UUID id, String phone, String name, Instant createdAt, int conversations) {
}
