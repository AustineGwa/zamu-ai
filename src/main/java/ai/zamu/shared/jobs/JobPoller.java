package ai.zamu.shared.jobs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Polls the jobs table every second (spec: "Job runners poll every second"). Off in the test profile. */
@Component
@ConditionalOnProperty(name = "zamu.jobs.enabled", havingValue = "true", matchIfMissing = true)
class JobPoller {

    private static final Logger log = LoggerFactory.getLogger(JobPoller.class);

    private final JobRunner runner;

    JobPoller(JobRunner runner) {
        this.runner = runner;
    }

    @Scheduled(fixedDelayString = "${zamu.jobs.poll-interval-ms:1000}")
    void poll() {
        try {
            runner.pollOnce();
        } catch (RuntimeException e) {
            log.error("Job poll failed", e);
        }
    }
}
