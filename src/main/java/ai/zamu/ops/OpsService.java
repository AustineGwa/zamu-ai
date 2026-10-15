package ai.zamu.ops;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ai.zamu.audit.AuditEvent;
import ai.zamu.audit.AuditService;
import ai.zamu.shared.jobs.JobQueue;
import ai.zamu.shared.web.NotFoundException;

/** The admin console's Health and Review queue screens (spec: admin console; NFR-04, NFR-16). */
@Service
class OpsService {

    /** Spec: Monitoring and alerts. */
    static final int BACKLOG_LIMIT = 100;
    static final Duration OLDEST_DUE_LIMIT = Duration.ofMinutes(2);

    record Health(OpsRepository.JobCounts jobs, Duration oldestDueAge, boolean backlogAlert, int unprocessedWebhooks,
            int failedMessages24h, List<OpsRepository.DeadJob> deadJobs, List<OpsRepository.NumberQuality> numbers,
            List<String> revokedCalendars) {
    }

    private final OpsRepository repository;
    private final JobQueue jobs;
    private final AuditService audit;
    private final Clock clock;

    OpsService(OpsRepository repository, JobQueue jobs, AuditService audit, Clock clock) {
        this.repository = repository;
        this.jobs = jobs;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    Health health() {
        Instant now = clock.instant();
        OpsRepository.JobCounts counts = repository.jobCounts(now);
        Duration age = counts.oldestDue() == null ? Duration.ZERO : Duration.between(counts.oldestDue(), now);
        return new Health(counts, age, backlog(counts, age), repository.unprocessedWebhooks(now.minus(Duration.ofMinutes(5))),
                repository.failedMessagesSince(now.minus(Duration.ofHours(24))), repository.deadJobs(50),
                repository.numberQuality(), repository.revokedCalendars());
    }

    static boolean backlog(OpsRepository.JobCounts counts, Duration oldestDueAge) {
        return counts.due() > BACKLOG_LIMIT || oldestDueAge.compareTo(OLDEST_DUE_LIMIT) > 0;
    }

    @Transactional(readOnly = true)
    List<OpsRepository.ReviewItem> review(int days) {
        return repository.reviewItems(clock.instant().minus(Duration.ofDays(days)), 300);
    }

    /** NFR-04: a dead job goes back to the queue with fresh attempts. */
    @Transactional
    void retry(UUID jobId) {
        if (!jobs.retryDead(jobId)) {
            throw new NotFoundException("Job not found or not dead");
        }
        audit.record(AuditEvent.of(null, "job.retried", "job", jobId));
    }
}
