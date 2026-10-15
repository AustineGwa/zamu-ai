package ai.zamu.ai;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks on every model reply before it is sent (spec: AI answering, step 4). A failed check replaces the reply
 * with the safe fallback and a handover.
 */
final class Guardrails {

    /** Marks the system prompt; a reply that repeats it is leaking the instructions (AI-08). */
    static final String CANARY = "ZMR-7Q4K";

    static final int MAX_WORDS = 150;

    private static final Pattern NUMBER = Pattern.compile("\\d[\\d,]*(?:\\.\\d+)?");
    private static final Pattern LEAK = Pattern.compile(
            "(?i)system prompt|my instructions|\\bZMR-|you are the (whatsapp )?receptionist for");

    private Guardrails() {
    }

    /** Returns why the reply must not be sent, or empty when it passes. */
    static Optional<String> check(String reply, String context) {
        if (reply == null || reply.isBlank()) {
            return Optional.of("empty reply");
        }
        if (reply.contains(CANARY) || LEAK.matcher(reply).find()) {
            return Optional.of("reveals the instructions (AI-08)");
        }
        if (reply.strip().split("\\s+").length > MAX_WORDS) {
            return Optional.of("too long (AI-07)");
        }
        Set<String> allowed = numbers(context);
        for (String n : numbers(reply)) {
            if (!allowed.contains(n)) {
                return Optional.of("number not in the knowledge base (AI-05)");
            }
        }
        return Optional.empty();
    }

    /**
     * AI-05: every number in a reply (price, time, phone number) must appear in the context. Thousands separators
     * are ignored, so "KES 4,500" matches "4500"; times and phone numbers are checked part by part.
     */
    static Set<String> numbers(String text) {
        Set<String> out = new HashSet<>();
        if (text == null) {
            return out;
        }
        Matcher m = NUMBER.matcher(text);
        while (m.find()) {
            String n = m.group().replace(",", "");
            if (n.endsWith(".")) {
                n = n.substring(0, n.length() - 1);
            }
            out.add(n.replaceFirst("^0+(?=\\d)", ""));
        }
        return out;
    }
}
