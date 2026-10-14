package ai.zamu.shared.jobs;

/** Test helper: runs queued jobs until none are due (the runner is off in tests, so they are deterministic).
 * Registered by TestSupportConfig (@Import); deliberately not a @Component. */
public class JobDrain {

    private final JobRunner runner;

    JobDrain(JobRunner runner) {
        this.runner = runner;
    }

    /** Runs due jobs, including ones enqueued by the jobs it runs. Returns how many ran. */
    public int drain() {
        return runner.runDueJobsNow();
    }
}
