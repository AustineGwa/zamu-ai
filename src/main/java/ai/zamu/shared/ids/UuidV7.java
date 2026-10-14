package ai.zamu.shared.ids;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.UUID;

/**
 * Time-ordered UUIDv7 (RFC 9562) for primary keys, stored as BINARY(16) for index locality
 * (spec: Data model, column conventions).
 *
 * <p>IDs are strictly increasing within one JVM, even within the same millisecond or if the clock steps
 * back (RFC 9562 section 6.2, method 1: a 12-bit counter in rand_a). The job queue relies on this to run
 * jobs with the same lock key in the order they were created (MSG-05).
 */
public final class UuidV7 {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MAX_SEQUENCE = 0xFFF;

    private static long lastMillis = -1;
    private static int sequence;

    private UuidV7() {
    }

    public static UUID next() {
        return next(System.currentTimeMillis());
    }

    public static UUID next(Clock clock) {
        return next(clock.millis());
    }

    static UUID next(long epochMillis) {
        long millis;
        int seq;
        synchronized (UuidV7.class) {
            if (epochMillis > lastMillis) {
                lastMillis = epochMillis;
                sequence = RANDOM.nextInt(0x800); // start in the lower half to leave room to count up
            } else if (sequence < MAX_SEQUENCE) {
                sequence++;
            } else {
                lastMillis++;
                sequence = RANDOM.nextInt(0x800);
            }
            millis = lastMillis;
            seq = sequence;
        }

        // 48 bits of Unix time in milliseconds, 4 bits version (7), 12 bits sequence.
        long msb = ((millis & 0xFFFF_FFFF_FFFFL) << 16) | 0x7000L | (seq & 0xFFFL);

        // 2 bits variant (10), 62 bits random.
        long lsb = (RANDOM.nextLong() & 0x3FFF_FFFF_FFFF_FFFFL) | 0x8000_0000_0000_0000L;
        return new UUID(msb, lsb);
    }
}
