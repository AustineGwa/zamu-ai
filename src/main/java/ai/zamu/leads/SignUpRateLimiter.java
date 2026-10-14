package ai.zamu.leads;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

/**
 * Limits sign-ups to 5 per hour from one IP address, to keep bots from flooding the leads list. In memory and
 * per instance (Caffeine), which is enough for a public form; the IP address is never stored.
 */
@Component
class SignUpRateLimiter {

    static final int MAX_PER_HOUR = 5;

    private final Cache<String, AtomicInteger> counts = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofHours(1))
            .maximumSize(100_000)
            .build();

    /** Counts this attempt and says whether it is allowed. */
    boolean tryAcquire(String clientIp) {
        AtomicInteger count = counts.get(clientIp == null ? "unknown" : clientIp, key -> new AtomicInteger());
        return count.incrementAndGet() <= MAX_PER_HOUR;
    }
}
