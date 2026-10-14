package ai.zamu.shared.mail;

import java.util.Objects;

/** A plain-text email. Login codes and weekly reports (NOT-03). */
public record EmailMessage(String to, String subject, String textBody) {

    public EmailMessage {
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(textBody, "textBody");
    }
}
