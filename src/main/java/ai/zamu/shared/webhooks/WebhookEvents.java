package ai.zamu.shared.webhooks;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ai.zamu.shared.ids.TenantId;
import ai.zamu.shared.ids.UuidV7;

/**
 * Stores raw webhook payloads (spec: webhook_events, "kept 30 days"). The spec names two owners for the table
 * (whatsapp and payments); it lives in shared so that both features use it without touching each other.
 */
@Service
public class WebhookEvents {

    private static final Logger log = LoggerFactory.getLogger(WebhookEvents.class);
    static final Duration RETENTION = Duration.ofDays(30);

    private final WebhookEventRepository repository;
    private final Clock clock;

    WebhookEvents(WebhookEventRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /** Stores the payload exactly as received. {@code tenantId} is null if the payload can't be routed. */
    @Transactional
    public UUID store(WebhookSource source, TenantId tenantId, String rawPayload) {
        UUID id = UuidV7.next(clock);
        repository.insert(id, source, tenantId, rawPayload, clock.instant());
        return id;
    }

    @Transactional(readOnly = true)
    public Optional<String> payload(TenantId tenantId, UUID eventId) {
        return repository.findPayload(tenantId, eventId);
    }

    @Transactional
    public void markProcessed(TenantId tenantId, UUID eventId) {
        repository.markProcessed(tenantId, eventId, clock.instant());
    }

    @Scheduled(cron = "0 41 3 * * *", zone = "Africa/Nairobi")
    @Transactional
    public void purgeOld() {
        int deleted = repository.deleteReceivedBefore(clock.instant().minus(RETENTION));
        if (deleted > 0) {
            log.info("Deleted {} webhook payloads older than 30 days", deleted);
        }
    }
}
