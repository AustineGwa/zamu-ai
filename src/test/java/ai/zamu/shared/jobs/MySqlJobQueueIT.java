package ai.zamu.shared.jobs;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;

import ai.zamu.shared.ids.TenantId;
import ai.zamu.shared.ids.UuidV7;
import ai.zamu.shared.ids.Uuids;
import ai.zamu.support.AbstractMySqlIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Background jobs on MySQL (spec: Background jobs without Redis; NFR-04; MSG-05). */
@Import(MySqlJobQueueIT.Handlers.class)
class MySqlJobQueueIT extends AbstractMySqlIT {

    @Autowired
    MySqlJobQueue queue;

    @Autowired
    JobRunner runner;

    @Autowired
    Handlers handlers;

    private final TenantId tenant = TenantId.of(UuidV7.next());

    @BeforeEach
    void clearHandlers() {
        handlers.ran.clear();
    }

    @Test
    void runsTheHandlerAndMarksTheJobDone() {
        UUID id = queue.enqueue(NewJob.of("test_ok", tenant, Map.of("n", 1)));

        assertThat(runner.runDueJobsNow()).isEqualTo(1);

        assertThat(handlers.ran).containsExactly("1");
        assertThat(status(id)).isEqualTo("done");
    }

    @Test
    void jobsScheduledForLaterWaitUntilDue() {
        queue.enqueue(NewJob.of("test_ok", tenant, Map.of("n", 1)).runAt(clock.instant().plus(Duration.ofHours(24))));

        assertThat(runner.runDueJobsNow()).isZero();
        clock.advance(Duration.ofHours(24));
        assertThat(runner.runDueJobsNow()).isEqualTo(1);
    }

    @Test
    void NFR_04_failedJobRetriesWithExponentialBackoffThenGoesDead() {
        UUID id = queue.enqueue(NewJob.of("test_fail", tenant, Map.of()).maxAttempts(3));

        runner.runDueJobsNow();
        assertThat(status(id)).isEqualTo("failed");
        assertThat(attempts(id)).isEqualTo(1);

        assertThat(runner.runDueJobsNow()).as("back-off: not due yet").isZero();
        clock.advance(MySqlJobQueue.backoff(1).plusSeconds(1));
        runner.runDueJobsNow();
        assertThat(attempts(id)).isEqualTo(2);
        assertThat(status(id)).isEqualTo("failed");

        clock.advance(MySqlJobQueue.backoff(2).plusSeconds(1));
        runner.runDueJobsNow();
        assertThat(status(id)).isEqualTo("dead");
        assertThat(attempts(id)).isEqualTo(3);
        assertThat(lastError(id)).contains("IllegalStateException");

        assertThat(queue.retryDead(id)).isTrue();
        assertThat(status(id)).isEqualTo("queued");
        assertThat(attempts(id)).isZero();
    }

    @Test
    void NFR_04_backoffDoublesAndIsCapped() {
        assertThat(MySqlJobQueue.backoff(1)).isEqualTo(Duration.ofSeconds(10));
        assertThat(MySqlJobQueue.backoff(2)).isEqualTo(Duration.ofSeconds(20));
        assertThat(MySqlJobQueue.backoff(4)).isEqualTo(Duration.ofSeconds(80));
        assertThat(MySqlJobQueue.backoff(30)).isEqualTo(Duration.ofMinutes(15));
    }

    @Test
    void unknownJobTypeGoesStraightToDead() {
        UUID id = queue.enqueue(NewJob.of("no_such_type", tenant, Map.of()));
        runner.runDueJobsNow();
        assertThat(status(id)).isEqualTo("dead");
    }

    @Test
    void MSG_05_neverClaimsTwoJobsWithTheSameLockKey() {
        UUID first = queue.enqueue(NewJob.of("test_ok", tenant, Map.of("n", 1)).lockKey("conv-1"));
        UUID second = queue.enqueue(NewJob.of("test_ok", tenant, Map.of("n", 2)).lockKey("conv-1"));
        UUID other = queue.enqueue(NewJob.of("test_ok", tenant, Map.of("n", 3)).lockKey("conv-2"));

        List<Job> claimed = queue.claim(10);
        assertThat(claimed).extracting(Job::id).containsExactlyInAnyOrder(first, other);

        assertThat(queue.claim(10)).as("conv-1 is busy").isEmpty();

        queue.markDone(first);
        assertThat(queue.claim(10)).extracting(Job::id).containsExactly(second);
    }

    @Test
    void MSG_05_jobsWithTheSameLockKeyRunInTheOrderReceived() {
        for (int n = 1; n <= 5; n++) {
            queue.enqueue(NewJob.of("test_ok", tenant, Map.of("n", n)).lockKey("conv-9"));
        }
        runner.runDueJobsNow();
        assertThat(handlers.ran).containsExactly("1", "2", "3", "4", "5");
    }

    @Test
    void MSG_05_databaseRejectsASecondRunningJobForTheSameLockKey() {
        UUID a = queue.enqueue(NewJob.of("test_ok", tenant, Map.of()).lockKey("conv-x"));
        UUID b = queue.enqueue(NewJob.of("test_ok", tenant, Map.of()).lockKey("conv-x"));
        jdbc.sql("UPDATE jobs SET status = 'running' WHERE id = :id").param("id", Uuids.toBytes(a)).update();

        assertThatThrownBy(() -> jdbc.sql("UPDATE jobs SET status = 'running' WHERE id = :id")
                .param("id", Uuids.toBytes(b)).update())
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void aJobWhoseLeaseExpiredIsPickedUpAgain() {
        UUID id = queue.enqueue(NewJob.of("test_ok", tenant, Map.of("n", 1)));
        assertThat(queue.claim(10)).hasSize(1); // runner "crashes" here

        assertThat(queue.claim(10)).isEmpty();
        clock.advance(Duration.ofMinutes(3));
        assertThat(queue.releaseExpiredLeases()).isEqualTo(1);

        List<Job> again = queue.claim(10);
        assertThat(again).extracting(Job::id).containsExactly(id);
        assertThat(again.getFirst().attempt()).isEqualTo(2);
    }

    @Test
    void NFR_09_theJobCarriesItsTenant() {
        queue.enqueue(NewJob.of("test_ok", tenant, Map.of("n", 1)));
        assertThat(queue.claim(1)).extracting(Job::tenantId).containsExactly(tenant);
    }

    private String status(UUID id) {
        return jdbc.sql("SELECT status FROM jobs WHERE id = :id").param("id", Uuids.toBytes(id)).query(String.class).single();
    }

    private int attempts(UUID id) {
        return jdbc.sql("SELECT attempts FROM jobs WHERE id = :id").param("id", Uuids.toBytes(id)).query(Integer.class).single();
    }

    private String lastError(UUID id) {
        return jdbc.sql("SELECT last_error FROM jobs WHERE id = :id").param("id", Uuids.toBytes(id)).query(String.class).single();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Handlers {

        final List<String> ran = new CopyOnWriteArrayList<>();

        @Bean
        JobHandler okHandler() {
            return new JobHandler() {
                @Override
                public String type() {
                    return "test_ok";
                }

                @Override
                public void handle(Job job) {
                    ran.add(job.payloadJson().replaceAll("\\D", ""));
                }
            };
        }

        @Bean
        JobHandler failingHandler() {
            return new JobHandler() {
                @Override
                public String type() {
                    return "test_fail";
                }

                @Override
                public void handle(Job job) {
                    throw new IllegalStateException("provider down");
                }
            };
        }
    }
}
