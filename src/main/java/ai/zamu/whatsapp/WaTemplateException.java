package ai.zamu.whatsapp;

/** A template can't be saved or submitted; the message is safe to show. */
class WaTemplateException extends RuntimeException {

    WaTemplateException(String message) {
        super(message);
    }
}
