package ai.zamu.users;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ai.zamu.shared.crypto.CodeHasher;
import ai.zamu.shared.ids.UuidV7;
import ai.zamu.shared.logging.Masking;
import ai.zamu.shared.mail.EmailMessage;
import ai.zamu.shared.mail.EmailSender;

/**
 * Email one-time-code login (spec: Login screen; POST /login/code and /login/verify; NOT-03).
 *
 * <p>Behaviour is the same whether or not the email has an account, so the login page never reveals who
 * has one: a code row is stored (and counted for the rate limit) either way, and the email is only sent to
 * active users.
 */
@Service
class LoginCodeService {

    private static final Logger log = LoggerFactory.getLogger(LoginCodeService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    enum RequestOutcome { ACCEPTED, RATE_LIMITED }

    private final LoginCodeRepository codes;
    private final UserRepository users;
    private final CodeHasher hasher;
    private final EmailSender emailSender;
    private final LoginProperties properties;
    private final Clock clock;

    LoginCodeService(LoginCodeRepository codes, UserRepository users, CodeHasher hasher, EmailSender emailSender,
            LoginProperties properties, Clock clock) {
        this.codes = codes;
        this.users = users;
        this.hasher = hasher;
        this.emailSender = emailSender;
        this.properties = properties;
        this.clock = clock;
    }

    public static String normalise(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    @Transactional
    public RequestOutcome requestCode(String rawEmail) {
        String email = normalise(rawEmail);
        Instant now = clock.instant();
        if (codes.countCreatedSince(email, now.minus(Duration.ofHours(1))) >= properties.maxCodesPerHour()) {
            log.info("Login code rate limit reached for {}", Masking.email(email));
            return RequestOutcome.RATE_LIMITED;
        }
        String code = "%06d".formatted(RANDOM.nextInt(1_000_000));
        codes.insert(new LoginCode(UuidV7.next(clock), email, hasher.hash(code),
                now.plus(properties.codeValidity()), 0, null, now));

        Optional<User> user = users.findActiveByEmail(email);
        if (user.isPresent()) {
            emailSender.send(loginEmail(email, user.get().name(), code));
        }
        return RequestOutcome.ACCEPTED;
    }

    /** Returns the active user if the code is right. Wrong guesses count against the code (and are committed). */
    @Transactional
    public Optional<User> verify(String rawEmail, String rawCode) {
        String email = normalise(rawEmail);
        String code = rawCode == null ? "" : rawCode.replaceAll("\\s", "");
        Instant now = clock.instant();

        Optional<LoginCode> latest = codes.findLatestUsableForUpdate(email, now);
        if (latest.isEmpty() || latest.get().attempts() >= properties.maxAttemptsPerCode()) {
            return Optional.empty();
        }
        LoginCode stored = latest.get();
        if (!code.matches("\\d{6}") || !hasher.matches(code, stored.codeHash())) {
            codes.incrementAttempts(stored.id());
            return Optional.empty();
        }
        codes.markConsumed(stored.id(), now);
        return users.findActiveByEmail(email);
    }

    /** Housekeeping: code rows hold email addresses, so they are deleted after a day (NFR-11 spirit). */
    @Scheduled(cron = "0 17 * * * *")
    @Transactional
    public void purgeOldCodes() {
        int deleted = codes.deleteCreatedBefore(clock.instant().minus(properties.codeRetention()));
        if (deleted > 0) {
            log.info("Purged {} old login codes", deleted);
        }
    }

    private EmailMessage loginEmail(String email, String name, String code) {
        long minutes = properties.codeValidity().toMinutes();
        String body = """
                Hello %s,

                Your Zamu login code is %s

                It expires in %d minutes. If you didn't ask for this code, you can ignore this email; nobody can \
                log in without it.

                Zamu
                """.formatted(name, code, minutes);
        return new EmailMessage(email, "Your Zamu login code: " + code, body);
    }
}
