package ai.zamu.conversations;

import java.util.List;

/** A WhatsApp template to send: approved name, language and body parameters in order. */
public record TemplateRef(String name, String language, List<String> params) {

    public TemplateRef {
        params = params == null ? List.of() : List.copyOf(params);
    }
}
