package ai.zamu.bookings;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * zamu.google: the Google Cloud OAuth client used for every tenant's calendar (BK-01). Register the redirect URI
 * {base-url}/admin/calendar/callback on the client. Blank client ID means calendars can't be connected; slots
 * then use business hours and Zamu's own bookings only.
 */
@ConfigurationProperties("zamu.google")
record GoogleProperties(String clientId, String clientSecret, Duration timeout) {

    GoogleProperties {
        timeout = timeout == null ? Duration.ofSeconds(8) : timeout;
    }

    boolean configured() {
        return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
    }
}
