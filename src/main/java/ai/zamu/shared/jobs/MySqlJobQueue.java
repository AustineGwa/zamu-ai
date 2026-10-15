package ai.zamu.shared.jobs;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import ai.zamu.shared.ids.TenantId;
import ai.zamu.shared.ids.UuidV7;
import ai.zamu.shared.ids.Uuids;
import ai.zamu.shared.security.CrossTenant;
import ai.zamu.shared.time.ZamuTime;

import tools.jackson.databind.ObjectMapper;

/**
 * {@link JobQueue} on the MySQL jobs table (spec: Background jobs without Redis; NFR-04; MSG-05).
 *
 * <p>Claiming uses {@code SELECT ... FOR UPDATE SKIP LOCKED} so several runners (and several app
 * instances) share the table safely. The NOT EXISTS subquery is a separate query block, so neither MySQL nor H2
 * locks the rows it reads; only the selected jobs are locked. A job with a lock key is only claimable when it is the oldest unfinished
 * job for that key and nothing with that key is running; the {@code uk_jobs_running_lock_key} unique index
 * backs this up at the database level.
 */
@Component
class MySqlJobQueue implements JobQueue {

    private static final Logger log = LoggerFactory.getLogger(MySqlJobQueue.class);

    static final String QUEUED = "queued";
    static final String RUNNING = "running";
    static final String DONE = "done";
    static final String FAILED = "failed";
    static final String DEAD = "dead";

    /** First retry after 10s, then 20s, 40s, 80s ... capped at 15 minutes. */
    private static final Duration BASE_BACKOFF = Duration.ofSeconds(5);
    private static final Duration MAX_BACKOFF = Duration.ofMinutes(15);

    private final JdbcClient jdbc;
    private final TransactionTemplate tx;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Duration lease;

    MySqlJobQueue(JdbcClient jdbc, TransactionTemplate tx, ObjectMapper objectMapper, Clock clock,
            @Value("${zamu.jobs.lease:2m}") Duration lease) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.lease = lease;
    }

    @Override
    @CrossTenant("The job carries its own tenant_id; this is the shared queue writer")
    public UUID enqueue(NewJob job) {
        Instant now = clock.instant();
        UUID id = UuidV7.next(clock);
        jdbc.sql("""
                INSERT INTO jobs (id, type, tenant_id, payload, run_at, status, attempts, max_attempts,
                                  lock_key, created_at, updated_at)
                VALUES (:id, :type, :tenantId, :payload, :runAt, 'queued', 0, :maxAttempts,
                        :lockKey, :now, :now)
                """)
                .param("id", Uuids.toBytes(id))
                .param("type", job.type())
                .param("tenantId", job.tenantId() == null ? null : job.tenantId().bytes())
                .param("payload", objectMapper.writeValueAsString(job.payload()))
                .param("runAt", ZamuTime.toDb(job.runAt() == null ? now : job.runAt()))
                .param("maxAttempts", job.maxAttempts())
                .param("lockKey", job.lockKey())
                .param("now", ZamuTime.toDb(now))
                .update();
        return id;
    }

    @Override
    @CrossTenant("Admin console retry of a dead job, operator or admin only")
    public boolean retryDead(UUID jobId) {
        Instant now = clock.instant();
        return jdbc.sql("""
                UPDATE jobs SET status = 'queued', attempts = 0, run_at = :now, last_error = NULL,
                                lease_until = NULL, updated_at = :now
                 WHERE id = :id AND status = 'dead'
                """)
                .param("id", Uuids.toBytes(jobId))
                .param("now", ZamuTime.toDb(now))
                .update() == 1;
    }

    /**
     * Claims up to {@code limit} due jobs and marks them running with a lease, in one short transaction that
     * commits before any work starts.
     */
    @CrossTenant("Job runner claims work for every tenant; each job keeps its own tenant_id")
    List<Job> claim(int limit) {
        if (limit <= 0) {
            return List.of();
        }
        List<Job> claimed = tx.execute(status -> {
            Instant now = clock.instant();
            List<Job> candidates = jdbc.sql("""
                    SELECT j.id, j.type, j.tenant_id, j.payload, j.attempts, j.max_attempts, j.lock_key
                      FROM jobs j
                     WHERE j.status IN ('queued', 'failed')
                       AND j.run_at <= :now
                       AND (j.lock_key IS NULL OR NOT EXISTS (
                            SELECT 1 FROM jobs o
                             WHERE o.lock_key = j.lock_key
                               AND o.id <> j.id
                               AND (o.status = 'running'
                                    OR (o.status IN ('queued', 'failed') AND o.id < j.id))))
                     ORDER BY j.run_at, j.id
                     LIMIT :limit
                     FOR UPDATE SKIP LOCKED
                    """)
                    .param("now", ZamuTime.toDb(now))
                    .param("limit", limit)
                    .query((rs, rowNum) -> new Job(
                            Uuids.read(rs, "id"),
                            rs.getString("type"),
                            tenantOrNull(Uuids.read(rs, "tenant_id")),
                            rs.getString("payload"),
                            rs.getInt("attempts") + 1,
                            rs.getInt("max_attempts"),
                            rs.getString("lock_key")))
                    .list();

            List<Job> running = new ArrayList<>();
            for (Job job : candidates) {
                try {
                    int updated = jdbc.sql("""
                            UPDATE jobs SET status = 'running', attempts = attempts + 1, lease_until = :leaseUntil,
                                            updated_at = :now
                             WHERE id = :id AND status IN ('queued', 'failed')
                            """)
                            .param("id", Uuids.toBytes(job.id()))
                            .param("leaseUntil", ZamuTime.toDb(now.plus(lease)))
                            .param("now", ZamuTime.toDb(now))
                            .update();
                    if (updated == 1) {
                        running.add(job);
                    }
                } catch (DuplicateKeyException e) {
                    // Another runner started a job with the same lock_key a moment ago (MSG-05). Leave it queued.
                    log.debug("Skipped job {}: lock key busy", job.id());
                }
            }
            return running;
        });
        return claimed == null ? List.of() : claimed;
    }

    @CrossTenant("Updates a job by its own ID after the runner claimed it")
    void markDone(UUID jobId) {
        Instant now = clock.instant();
        jdbc.sql("""
                UPDATE jobs SET status = 'done', lease_until = NULL, last_error = NULL, updated_at = :now
                 WHERE id = :id AND status = 'running'
                """)
                .param("id", Uuids.toBytes(jobId))
                .param("now", ZamuTime.toDb(now))
                .update();
    }

    /** Retries with exponential back-off, or moves the job to dead after its last attempt (NFR-04). */
    @CrossTenant("Updates a job by its own ID after the runner claimed it")
    void markFailed(Job job, String error) {
        Instant now = clock.instant();
        boolean dead = job.attempt() >= job.maxAttempts();
        jdbc.sql("""
                UPDATE jobs SET status = :status, run_at = :runAt, lease_until = NULL, last_error = :error,
                                updated_at = :now
                 WHERE id = :id AND status = 'running'
                """)
                .param("id", Uuids.toBytes(job.id()))
                .param("status", dead ? DEAD : FAILED)
                .param("runAt", ZamuTime.toDb(dead ? now : now.plus(backoff(job.attempt()))))
                .param("error", truncate(error))
                .param("now", ZamuTime.toDb(now))
                .update();
        if (dead) {
            log.warn("Job {} of type {} is dead after {} attempts", job.id(), job.type(), job.attempt());
        }
    }

    /** Jobs whose runner died mid-work are picked up again once their lease expires. */
    @CrossTenant("Maintenance across all tenants' jobs")
    int releaseExpiredLeases() {
        Instant now = clock.instant();
        return jdbc.sql("""
                UPDATE jobs
                   SET status = CASE WHEN attempts >= max_attempts THEN 'dead' ELSE 'failed' END,
                       run_at = :now, lease_until = NULL, last_error = 'Lease expired', updated_at = :now
                 WHERE status = 'running' AND lease_until < :now
                """)
                .param("now", ZamuTime.toDb(now))
                .update();
    }

    static Duration backoff(int attempt) {
        long factor = 1L << Math.min(attempt, 20);
        Duration delay = BASE_BACKOFF.multipliedBy(factor);
        return delay.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : delay;
    }

    private static TenantId tenantOrNull(UUID id) {
        return id == null ? null : TenantId.of(id);
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 2000 ? value : value.substring(0, 2000);
    }

    @Override
    public int purgeFinishedBefore(Instant cutoff) {
        return jdbc.sql("DELETE FROM jobs WHERE status IN ('done', 'dead') AND updated_at < :cutoff")
                .param("cutoff", ZamuTime.toDb(cutoff))
                .update();
    }
}
