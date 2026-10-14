package ai.zamu.whatsapp;

import java.util.Locale;

/** Meta's review state for a template (spec: wa_templates.meta_status). */
public enum WaTemplateStatus {
    DRAFT("Draft"),
    PENDING("Waiting for Meta"),
    APPROVED("Approved"),
    REJECTED("Rejected");

    private final String label;

    WaTemplateStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public String dbValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static WaTemplateStatus fromDb(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }

    /**
     * Meta's template webhook events. ASSUMPTION: PAUSED and DISABLED stop sending, so they count as rejected here;
     * FLAGGED and others leave the status unchanged.
     */
    static WaTemplateStatus fromMetaEvent(String event) {
        return switch (event == null ? "" : event.toUpperCase(Locale.ROOT)) {
            case "APPROVED" -> APPROVED;
            case "REJECTED", "PAUSED", "DISABLED" -> REJECTED;
            case "PENDING", "IN_APPEAL" -> PENDING;
            default -> null;
        };
    }
}
