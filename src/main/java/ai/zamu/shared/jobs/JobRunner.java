package ai.zamu.shared.jobs;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Claims due jobs and runs them on virtual threads (spec: Job runners). The {@link JobPoller} calls
 * {@link #pollOnce()} every second when {@code zamu.jobs.enabled=true}; tests call {@link #runDueJobsNow()}.
 */
@Component
class JobRunner implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(JobRunner.class);

    private final MySqlJobQueue queue;
    private final Map<String, JobHandler> handlers;
    private final Semaphore capacity;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    JobRunner(MySqlJobQueue queue, List<JobHandler> handlers, @Value("${zamu.jobs.batch-size:10}") int maxConcurrent) {
        this.queue = queue;
        this.handlers = handlers.stream().collect(Collectors.toMap(JobHandler::type, Function.identity()));
        this.capacity = new Semaphore(maxConcurrent);
    }

    /** Claims as many jobs as there is free capacity for and starts them without waiting. */
    void pollOnce() {
        queue.releaseExpiredLeases();
        int free = capacity.availablePermits();
        if (free == 0) {
            return;
        }
        for (Job job : queue.claim(free)) {
            capacity.acquireUninterruptibly();
            executor.execute(() -> {
                try {
                    run(job);
                } finally {
                    capacity.release();
                }
            });
        }
    }

    /** Runs every job that is due now and waits for them; repeats until nothing is due. For tests. */
    int runDueJobsNow() {
        int total = 0;
        while (true) {
            queue.releaseExpiredLeases();
            List<Job> jobs = queue.claim(50);
            if (jobs.isEmpty()) {
                return total;
            }
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (Job job : jobs) {
                futures.add(CompletableFuture.runAsync(() -> run(job), executor));
            }
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
            total += jobs.size();
        }
    }

    private void run(Job job) {
        MDC.put("jobId", "job-" + job.id());
        try {
            JobHandler handler = handlers.get(job.type());
            if (handler == null) {
                log.error("No handler for job type {}", job.type());
                queue.markFailed(new Job(job.id(), job.type(), job.tenantId(), job.payloadJson(),
                        job.maxAttempts(), job.maxAttempts(), job.lockKey()), "No handler for job type " + job.type());
                return;
            }
            handler.handle(job);
            queue.markDone(job.id());
        } catch (Exception e) {
            // Exception messages can contain personal data from providers, so only the type goes to the log
            // (NFR-13); the message is kept in the jobs table for operators.
            log.warn("Job {} ({}) attempt {} failed: {}", job.id(), job.type(), job.attempt(), e.getClass().getSimpleName());
            queue.markFailed(job, e.getClass().getSimpleName() + ": " + e.getMessage());
        } finally {
            MDC.remove("jobId");
        }
    }

    @Override
    public void destroy() {
        executor.shutdown();
    }
}
