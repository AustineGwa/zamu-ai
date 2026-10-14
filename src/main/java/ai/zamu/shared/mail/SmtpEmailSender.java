package ai.zamu.shared.mail;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** SMTP delivery through Spring Mail, for the mysql profile. Mailpit when run locally (compose.yaml); the provider's SMTP in production. */
@Component
@Profile("!h2")
class SmtpEmailSender implements EmailSender {

    private final JavaMailSender mailSender;
    private final String from;

    SmtpEmailSender(JavaMailSender mailSender, @Value("${zamu.mail.from}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public void send(EmailMessage message) {
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(from);
        mail.setTo(message.to());
        mail.setSubject(message.subject());
        mail.setText(message.textBody());
        try {
            mailSender.send(mail);
        } catch (MailException e) {
            throw new EmailDeliveryException("Email could not be sent", e);
        }
    }
}
