package ai.zamu.ops;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import ai.zamu.shared.mail.EmailMessage;
import ai.zamu.shared.mail.EmailSender;
import ai.zamu.users.UserService;

/**
 * Operator alerts (spec: Monitoring and alerts) by email to the Zamu team: job backlog over 100 or the oldest due
 * job waiting over 2 minutes; webhooks stored but not processed; jobs that died. Each alert repeats at most every
 * 30 minutes while the problem lasts. Also publishes queue gauges for metrics (NFR-16).
 *
 * <p>Calendar access loss is alerted where it's detected (bookings); WhatsApp quality and AI error rates show on
 * the Health screen (ASSUMPTION: alerting on them needs history this version doesn't keep).
 */
@Component
class OpsMonitor {

    private static final Logger log = LoggerFactory.getLogger(OpsMonitor.class);
    static final Duration REPEAT = Duration.ofMinutes(30);

    private final OpsRepository repository;
    private final UserService users;
    private final EmailSender email;
    private final Clock clock;
    private final String baseUrl;
    private final boolean enabled;
    private final Map<String, Instant> lastSent = new ConcurrentHashMap<>();
    private volatile Instant lastCheck;
    private volatile OpsRepository.JobCounts lastCounts;

    OpsMonitor(OpsRepository repository, UserService users, EmailSender email, Clock clock, MeterRegistry meters,
            @Value("${zamu.base-url}") String baseUrl, @Value("${zamu.jobs.enabled:true}") boolean enabled) {
        this.repository = repository;
        this.users = users;
        this.email = email;
        this.clock = clock;
        this.baseUrl = baseUrl;
        this.enabled = enabled;
        Gauge.builder("zamu.jobs.due", this, m -> m.lastCounts == null ? 0 : m.lastCounts.due()).register(meters);
        Gauge.builder("zamu.jobs.dead", this, m -> m.lastCounts == null ? 0 : m.lastCounts.dead()).register(meters);
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    void scheduled() {
        if (!enabled) {
            return;
        }
        try {
            check();
        } catch (RuntimeException e) {
            log.error("Monitoring check failed", e);
        }
    }

    /** Returns how many alerts were sent. */
    int check() {
        Instant now = clock.instant();
        int sent = 0;
        OpsRepository.JobCounts counts = repository.jobCounts(now);
        lastCounts = counts;
        Duration age = counts.oldestDue() == null ? Duration.ZERO : Duration.between(counts.oldestDue(), now);
        if (OpsService.backlog(counts, age)) {
            sent += alert("backlog", "Zamu job queue is backed up", "%d jobs are due and the oldest has waited %d minutes."
                    .formatted(counts.due(), age.toMinutes()), now);
        }
        int webhooks = repository.unprocessedWebhooks(now.minus(Duration.ofMinutes(5)));
        if (webhooks > 5) {
            sent += alert("webhooks", "WhatsApp messages are not being processed",
                    "%d webhook payloads have waited more than 5 minutes.".formatted(webhooks), now);
        }
        Instant since = lastCheck == null || lastCheck.isAfter(now) ? now.minus(Duration.ofMinutes(5)) : lastCheck;
        int died = repository.deadSince(since);
        if (died > 0) {
            // Each check only counts jobs that died since the last one, so this alert is never held back.
            sent += alert(null, "Background jobs failed for good",
                    "%d jobs moved to dead after their last retry.".formatted(died), now);
        }
        lastCheck = now;
        return sent;
    }

    /** Tests: start from a clean state, as after a restart. */
    void reset() {
        lastSent.clear();
        lastCheck = null;
    }

    /** {@code key} null: always send; otherwise at most once per {@link #REPEAT} for that key. */
    private int alert(String key, String subject, String detail, Instant now) {
        if (key != null) {
            Instant previous = lastSent.get(key);
            if (previous != null && !previous.isAfter(now) && Duration.between(previous, now).compareTo(REPEAT) < 0) {
                return 0;
            }
            lastSent.put(key, now);
        }
        log.warn("Operator alert: {}", subject);
        for (String operator : users.activePlatformEmails()) {
            email.send(new EmailMessage(operator, "Zamu alert: " + subject, detail
                    + "\n\nHealth: " + baseUrl + "/admin/health\n\nZamu"));
        }
        return 1;
    }
}
