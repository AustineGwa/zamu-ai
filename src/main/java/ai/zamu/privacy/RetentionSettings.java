package ai.zamu.privacy;

/**
 * NFR-11 per tenant. Payment records are always kept 7 years, and audit and usage records are kept for billing and
 * accountability (ASSUMPTION: the spec lists those as outside "everything else").
 */
public record RetentionSettings(int conversationMonths, int offboardPurgeDays) {

    public static final RetentionSettings DEFAULTS = new RetentionSettings(12, 30);
}
