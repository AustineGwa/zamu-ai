package ai.zamu.ai;

import java.util.List;

import ai.zamu.knowledge.Retriever.RetrievedChunk;

/**
 * The one way Zamu calls a language model (spec: ai/AiClient). Provider-agnostic (AI-10): implemented over Spring
 * AI's ChatClient for whichever provider is configured, with an offline stand-in when none is.
 */
interface AiClient {

    /** @throws AiUnavailableException when no provider answered in time */
    AiCompletion complete(AiRequest request);

    /** Whether a real model is configured (false means the offline stand-in answers). */
    boolean live();

    /**
     * @param chunks   the retrieved knowledge, also given to the offline stand-in
     * @param language the customer's language
     */
    record AiRequest(String system, String user, ModelTier tier, List<RetrievedChunk> chunks, String language,
            String question) {
    }

    record AiCompletion(String text, String model, long tokensIn, long tokensOut) {
    }
}
