package ai.zamu.shared.jobs;

/**
 * Does the work for one job type. Features register handlers as Spring beans. A handler that throws is
 * retried with exponential back-off, then moved to dead (NFR-04), so handlers must be idempotent.
 */
public interface JobHandler {

    String type();

    void handle(Job job) throws Exception;
}
