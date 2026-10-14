package ai.zamu.shared.web;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import ai.zamu.shared.ids.TenantId;

/**
 * Server-sent events for the live inbox (spec: Live updates, GET /app/stream). Each browser tab of a tenant user
 * subscribes; features publish small events ("inbox", "conversation-{id}", "handover") after their transaction
 * commits. Events carry IDs and display names only, never message text.
 *
 * <p>In-process: with more than one application instance, a user only receives events produced on the instance
 * their browser is connected to. Version 1 runs one instance; a shared broker can replace this class later.
 */
@Component
public class LiveUpdates {

    private static final Logger log = LoggerFactory.getLogger(LiveUpdates.class);
    private static final long TIMEOUT_MS = Duration.ofMinutes(30).toMillis();

    private final Map<UUID, List<SseEmitter>> byTenant = new ConcurrentHashMap<>();

    public SseEmitter subscribe(TenantId tenantId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        List<SseEmitter> emitters = byTenant.computeIfAbsent(tenantId.value(), key -> new CopyOnWriteArrayList<>());
        emitters.add(emitter);
        Runnable remove = () -> emitters.remove(emitter);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(error -> remove.run());
        try {
            emitter.send(SseEmitter.event().comment("connected"));
        } catch (IOException e) {
            remove.run();
        }
        return emitter;
    }

    /** Sends an event to every connected user of the tenant, after the current transaction commits. */
    public void publish(TenantId tenantId, String eventName, String data) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send(tenantId.value(), eventName, data);
                }
            });
        } else {
            send(tenantId.value(), eventName, data);
        }
    }

    /** Keeps idle connections open through proxies. */
    @Scheduled(fixedDelay = 25_000)
    void heartbeat() {
        byTenant.forEach((tenant, emitters) -> emitters.forEach(emitter -> {
            try {
                emitter.send(SseEmitter.event().comment("ping"));
            } catch (IOException | IllegalStateException e) {
                emitters.remove(emitter);
            }
        }));
    }

    int connections(TenantId tenantId) {
        return byTenant.getOrDefault(tenantId.value(), List.of()).size();
    }

    private void send(UUID tenant, String eventName, String data) {
        List<SseEmitter> emitters = byTenant.getOrDefault(tenant, List.of());
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(data == null ? "" : data));
            } catch (IOException | IllegalStateException e) {
                emitters.remove(emitter);
                log.debug("Dropped a closed live-update connection");
            }
        }
    }
}
