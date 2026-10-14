package ai.zamu.conversations;

import java.util.List;
import java.util.Optional;

import ai.zamu.shared.ids.TenantId;

/**
 * Approved message templates, for staff replies outside the 24-hour window (HO-07). Implemented by the whatsapp
 * package, which owns wa_templates; declared here so conversations never depends on whatsapp.
 */
public interface TemplateCatalog {

    List<TemplateOption> approved(TenantId tenantId);

    default Optional<TemplateOption> find(TenantId tenantId, String name, String language) {
        return approved(tenantId).stream()
                .filter(t -> t.name().equals(name) && t.language().equals(language))
                .findFirst();
    }

    /** An approved template; {@code body} has {{1}}, {{2}} placeholders. */
    record TemplateOption(String name, String language, String body, int parameterCount) {

        public String render(List<String> params) {
            String out = body;
            for (int i = 0; i < params.size(); i++) {
                out = out.replace("{{" + (i + 1) + "}}", params.get(i));
            }
            return out;
        }
    }
}
