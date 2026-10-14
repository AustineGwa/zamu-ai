package ai.zamu.shared.config;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * Exactly one database profile must be active: {@code h2} (the default, local development) or {@code mysql}
 * (staging and production). Refuses to start otherwise, so that:
 * <ul>
 *   <li>the h2 profile, with its public development keys and login codes printed to the console, can never run
 *       alongside a real MySQL configuration;</li>
 *   <li>a mistyped profile (for example {@code SPRING_PROFILES_ACTIVE=prod}) fails with a clear message instead
 *       of starting without a database.</li>
 * </ul>
 */
@Component
class H2LocalProfileGuard {

    H2LocalProfileGuard(Environment environment) {
        check(environment);
    }

    static void check(Environment environment) {
        boolean h2 = environment.acceptsProfiles(Profiles.of("h2"));
        boolean mysql = environment.acceptsProfiles(Profiles.of("mysql"));
        if (h2 && mysql) {
            throw new IllegalStateException("Profiles h2 and mysql can't be active together. "
                    + "Use h2 (the default) for local development or mysql for staging and production.");
        }
        if (!h2 && !mysql) {
            throw new IllegalStateException("No database profile is active. Add h2 (local development) or mysql "
                    + "(staging and production) to the active profiles; they are currently "
                    + String.join(",", environment.getActiveProfiles()));
        }
    }
}
