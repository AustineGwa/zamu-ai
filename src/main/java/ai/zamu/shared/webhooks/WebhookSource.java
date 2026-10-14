package ai.zamu.shared.webhooks;

import java.util.Locale;

/** Who sent a webhook (spec: webhook_events.source). */
public enum WebhookSource {
    META, DARAJA, GOOGLE;

    String dbValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
