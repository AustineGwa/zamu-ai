package ai.zamu.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** A clock tests can move forward, to check expiry, back-off and leases without sleeping. */
public class MutableClock extends Clock {

    private volatile Instant now;

    public MutableClock(Instant start) {
        this.now = start;
    }

    public void advance(Duration duration) {
        now = now.plus(duration);
    }

    public void set(Instant instant) {
        now = instant;
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    /** A view in another zone that still follows this clock, so LocalDate.now(clock.withZone(z)) is right. */
    @Override
    public Clock withZone(ZoneId zone) {
        MutableClock base = this;
        return new Clock() {
            @Override
            public ZoneId getZone() {
                return zone;
            }

            @Override
            public Clock withZone(ZoneId other) {
                return base.withZone(other);
            }

            @Override
            public Instant instant() {
                return base.instant();
            }
        };
    }

    @Override
    public Instant instant() {
        return now;
    }
}
