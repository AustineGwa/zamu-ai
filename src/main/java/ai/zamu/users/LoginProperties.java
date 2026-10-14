package ai.zamu.users;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Login rules (spec: Dashboard screens, Login: "a 6-digit code by email, valid 10 minutes"; Auth routes:
 * "rate-limited to 5 per hour per email"). ASSUMPTION: 5 wrong guesses per code and 24-hour retention of
 * code rows are not in the spec.
 */
@ConfigurationProperties("zamu.login")
record LoginProperties(Duration codeValidity, int maxCodesPerHour, int maxAttemptsPerCode, Duration codeRetention) {
}
