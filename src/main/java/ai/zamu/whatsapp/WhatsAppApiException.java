package ai.zamu.whatsapp;

/**
 * A Cloud API call failed. Carries Meta's error code only; Meta's error text can echo message content, so it is
 * never logged (NFR-13).
 */
class WhatsAppApiException extends RuntimeException {

    private final String code;

    WhatsAppApiException(String code, Throwable cause) {
        super("WhatsApp API error " + code, cause);
        this.code = code;
    }

    String code() {
        return code;
    }
}
