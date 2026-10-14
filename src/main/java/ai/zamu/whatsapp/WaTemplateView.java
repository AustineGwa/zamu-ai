package ai.zamu.whatsapp;

import java.time.Instant;
import java.util.UUID;

/** A template as the admin page shows it. */
public record WaTemplateView(UUID id, String name, String category, String language, String body,
        WaTemplateStatus status, String reason, int parameterCount, Instant updatedAt) {
}
