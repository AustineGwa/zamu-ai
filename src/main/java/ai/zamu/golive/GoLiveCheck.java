package ai.zamu.golive;

/**
 * One item of the go-live checklist (Operations Manual: Testing and go-live checklist).
 *
 * @param required a required item must pass before the tenant can go live
 */
public record GoLiveCheck(String label, boolean passed, boolean required, String detail, String fixPath) {
}
