package ai.zamu.shared.jobs;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import ai.zamu.shared.ids.TenantId;
import ai.zamu.shared.ids.UuidV7;
import ai.zamu.support.TestSupportConfig;

import static org.assertj.core.api.Assertions.assertThat;

/** The job queue's claim SQL (FOR UPDATE SKIP LOCKED, lock keys) also runs on the local h2 profile. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:jobs-${random.uuid};MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "zamu.jobs.enabled=false", "zamu.local-demo.enabled=false"})
@ActiveProfiles("h2")
@Import(TestSupportConfig.class)
class H2JobQueueSmokeTest {

    @Autowired
    MySqlJobQueue queue;

    @Test
    void claimsOneJobPerLockKeyOnH2() {
        TenantId tenant = TenantId.of(UuidV7.next());
        UUID first = queue.enqueue(NewJob.of("x", tenant, Map.of("n", 1)).lockKey("conv-1"));
        queue.enqueue(NewJob.of("x", tenant, Map.of("n", 2)).lockKey("conv-1"));

        assertThat(queue.claim(10)).extracting(Job::id).containsExactly(first);
        assertThat(queue.claim(10)).isEmpty();
    }
}
