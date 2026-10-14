package ai.zamu.support;

import java.time.Instant;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Replaces the clock and the email sender for integration tests. */
@TestConfiguration(proxyBeanMethods = false)
public class TestSupportConfig {

    /** 14 October 2026, 06:00 UTC (09:00 in Nairobi). */
    public static final Instant START = Instant.parse("2026-10-14T06:00:00Z");

    @Bean
    @Primary
    MutableClock testClock() {
        return new MutableClock(START);
    }

    @Bean
    @Primary
    RecordingEmailSender recordingEmailSender() {
        return new RecordingEmailSender();
    }
}
