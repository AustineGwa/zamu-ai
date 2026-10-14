package ai.zamu.whatsapp;

import java.time.Instant;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A row of wa_templates. */
record WaTemplate(UUID id, UUID tenantId, String name, String category, String language, String body,
        WaTemplateStatus status, String reason, Instant createdAt, Instant updatedAt) {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{(\\d+)}}");

    /** The highest {{n}} in the body; bodies must use 1..n without gaps (checked when saving). */
    static int parameterCount(String body) {
        Matcher m = PLACEHOLDER.matcher(body == null ? "" : body);
        int max = 0;
        while (m.find()) {
            max = Math.max(max, Integer.parseInt(m.group(1)));
        }
        return max;
    }

    /** True when every placeholder from {{1}} to the highest is present. */
    static boolean placeholdersInOrder(String body) {
        int count = parameterCount(body);
        for (int i = 1; i <= count; i++) {
            if (!body.contains("{{" + i + "}}")) {
                return false;
            }
        }
        return true;
    }

    int parameterCount() {
        return parameterCount(body);
    }

    WaTemplateView toView() {
        return new WaTemplateView(id, name, category, language, body, status, reason, parameterCount(), updatedAt);
    }
}
