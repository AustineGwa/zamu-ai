package ai.zamu.shared.mail;

/** Sends email. SMTP today; swappable for an HTTP email API later. */
public interface EmailSender {

    /** Sends synchronously; throws {@link EmailDeliveryException} if the provider refuses or times out. */
    void send(EmailMessage message);
}
