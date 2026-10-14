package ai.zamu.conversations;

import java.util.List;

import jakarta.validation.constraints.Size;

/**
 * Staff reply: free text, or an approved template with its values (spec: POST /app/conversations/{id}/messages).
 * A template name present means a template reply.
 */
public record StaffReplyForm(
        @Size(max = 4096, message = "WhatsApp messages can be up to 4,096 characters") String text,
        String templateName,
        String templateLanguage,
        List<String> templateParams) {

    static StaffReplyForm empty() {
        return new StaffReplyForm("", null, null, List.of());
    }

    boolean isTemplate() {
        return templateName != null && !templateName.isBlank();
    }
}
