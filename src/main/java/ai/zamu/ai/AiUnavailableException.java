package ai.zamu.ai;

/** No provider gave an answer in time (AI-10). The caller hands the chat to staff. */
class AiUnavailableException extends RuntimeException {

    AiUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
