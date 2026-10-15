package ai.zamu.ai;

import java.time.Instant;
import java.util.UUID;

/** An evaluation run as pages show it. */
public record EvalRunView(UUID id, String status, String model, int total, int passed, int trapFailures,
        Instant createdAt, Instant finishedAt) {

    /** Spec: correct answers stay at 95% or above. */
    public static final double PASS_RATE = 0.95;

    public boolean finished() {
        return "done".equals(status);
    }

    public double passRate() {
        return total == 0 ? 0 : (double) passed / total;
    }

    public int percent() {
        return (int) Math.floor(passRate() * 100);
    }

    /** Spec: ships only at 95% or above with no trap question answered as if grounded. */
    public boolean meetsBar() {
        return finished() && total > 0 && passRate() >= PASS_RATE && trapFailures == 0;
    }
}
