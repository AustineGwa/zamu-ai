package ai.zamu.support;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ai.zamu.shared.mail.EmailMessage;
import ai.zamu.shared.mail.EmailSender;

/** Captures emails instead of sending them. */
public class RecordingEmailSender implements EmailSender {

    private static final Pattern CODE = Pattern.compile("\\b(\\d{6})\\b");

    private final List<EmailMessage> sent = new CopyOnWriteArrayList<>();

    @Override
    public void send(EmailMessage message) {
        sent.add(message);
    }

    public List<EmailMessage> sent() {
        return List.copyOf(sent);
    }

    public List<EmailMessage> sentTo(String email) {
        return sent.stream().filter(m -> m.to().equalsIgnoreCase(email)).toList();
    }

    /** The 6-digit code from the latest login email to this address. */
    public Optional<String> latestCodeFor(String email) {
        List<EmailMessage> messages = sentTo(email);
        if (messages.isEmpty()) {
            return Optional.empty();
        }
        Matcher matcher = CODE.matcher(messages.getLast().textBody());
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    public void clear() {
        sent.clear();
    }
}
