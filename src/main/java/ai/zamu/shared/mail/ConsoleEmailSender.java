package ai.zamu.shared.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import ai.zamu.shared.logging.Masking;

/**
 * Local h2 profile (the default) only: prints emails to the console instead of sending them, so login codes can be read
 * without Mailpit or Docker.
 *
 * <p>This deliberately logs the email body, including the login code, which NFR-13 forbids in any real
 * environment. It is acceptable only because the h2 profile holds throwaway demo data and
 * {@code H2LocalProfileGuard} refuses to start if it is combined with the mysql profile. The address is still masked.
 */
@Component
@Profile("h2")
class ConsoleEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(ConsoleEmailSender.class);

    @Override
    public void send(EmailMessage message) {
        log.warn("""

                ===== LOCAL EMAIL (h2 profile, not sent) =====
                To:      {}
                Subject: {}

                {}
                ==============================================""",
                Masking.email(message.to()), message.subject(), message.textBody());
    }
}
