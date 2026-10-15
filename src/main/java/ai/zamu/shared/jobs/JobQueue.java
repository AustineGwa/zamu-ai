package ai.zamu.shared.jobs;

import java.util.UUID;

/**
 * Queue for slow work (spec: Background jobs without Redis). Implemented on the MySQL jobs table today;
 * a Redis-backed implementation can replace it later without changing features.
 *
 * <p>Call {@link #enqueue} inside the caller's transaction: the job is only visible to runners once that
 * transaction commits, so a webhook's stored payload and its processing job appear together.
 */
public interface JobQueue {

    UUID enqueue(NewJob job);

    /** Moves a dead job back to queued with a fresh attempt count (admin console, NFR-04). */
    boolean retryDead(UUID jobId);

    /** NFR-11 housekeeping: deletes done and dead jobs last updated before the cutoff. Returns how many. */
    int purgeFinishedBefore(java.time.Instant cutoff);
}
