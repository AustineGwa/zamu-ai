package ai.zamu.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * {@link AiClient} over Spring AI (AI-10). Every ChatModel the configured starters created is a candidate: the
 * primary first, then the backup named by zamu.ai.backup-provider (a bean-name prefix such as "openAi"). Each call
 * is bounded by zamu.ai.timeout. With no ChatModel at all (local h2 profile, tests), {@link OfflineAiClient}
 * answers instead.
 *
 * <p>Options go through ChatClient, which merges them with the provider's defaults: the model name per tier
 * (AI-09), a low temperature and a reply-size limit.
 */
@Component
class AiGateway implements AiClient, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(AiGateway.class);
    static final int MAX_OUTPUT_TOKENS = 700;

    private record Provider(String name, ChatClient client) {
    }

    private final ApplicationContext context;
    private final AiProperties properties;
    private final OfflineAiClient offline;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private volatile List<Provider> providers;

    AiGateway(ApplicationContext context, AiProperties properties, OfflineAiClient offline) {
        this.context = context;
        this.properties = properties;
        this.offline = offline;
    }

    @Override
    public boolean live() {
        return !providers().isEmpty();
    }

    @Override
    public AiCompletion complete(AiRequest request) {
        List<Provider> candidates = providers();
        if (candidates.isEmpty()) {
            return offline.complete(request);
        }
        RuntimeException last = null;
        for (Provider provider : candidates) {
            try {
                return call(provider, request);
            } catch (RuntimeException e) {
                last = e;
                log.warn("AI provider {} failed ({}); {}", provider.name(), e.getClass().getSimpleName(),
                        candidates.size() > 1 ? "trying the next one" : "giving up");
            }
        }
        throw new AiUnavailableException("No AI provider answered", last);
    }

    private AiCompletion call(Provider provider, AiRequest request) {
        String model = properties.model(request.tier());
        CompletableFuture<ChatResponse> future = CompletableFuture.supplyAsync(() -> provider.client().prompt()
                .system(request.system())
                .user(request.user())
                .options(ChatOptions.builder().model(model).temperature(0.2).maxTokens(MAX_OUTPUT_TOKENS))
                .call()
                .chatResponse(), executor);
        ChatResponse response;
        try {
            response = future.get(properties.timeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new AiUnavailableException("Timed out after " + properties.timeout(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiUnavailableException("Interrupted", e);
        } catch (ExecutionException e) {
            throw e.getCause() instanceof RuntimeException r ? r : new AiUnavailableException("Call failed", e);
        }
        if (response == null || response.getResult() == null) {
            throw new AiUnavailableException("Empty response", null);
        }
        String text = response.getResult().getOutput().getText();
        Usage usage = response.getMetadata() == null ? null : response.getMetadata().getUsage();
        long in = usage == null || usage.getPromptTokens() == null ? 0 : usage.getPromptTokens();
        long out = usage == null || usage.getCompletionTokens() == null ? 0 : usage.getCompletionTokens();
        String usedModel = response.getMetadata() != null && response.getMetadata().getModel() != null
                && !response.getMetadata().getModel().isBlank() ? response.getMetadata().getModel() : model;
        return new AiCompletion(text == null ? "" : text, usedModel, in, out);
    }

    /** Resolved on first use, after Spring AI's auto-configuration has created its models. */
    private List<Provider> providers() {
        List<Provider> current = providers;
        if (current == null) {
            synchronized (this) {
                if (providers == null) {
                    providers = resolve();
                }
                current = providers;
            }
        }
        return current;
    }

    private List<Provider> resolve() {
        Map<String, ChatModel> models = context.getBeansOfType(ChatModel.class);
        String backup = properties.backupProvider() == null ? "" : properties.backupProvider().strip();
        List<Provider> primary = new ArrayList<>();
        List<Provider> backups = new ArrayList<>();
        models.forEach((name, model) -> {
            Provider p = new Provider(name, ChatClient.create(model));
            if (!backup.isEmpty() && name.toLowerCase().startsWith(backup.toLowerCase())) {
                backups.add(p);
            } else {
                primary.add(p);
            }
        });
        primary.addAll(backups);
        if (primary.isEmpty()) {
            log.info("No AI provider configured: the offline stand-in answers from the knowledge base");
        } else {
            log.info("AI providers in order: {}", primary.stream().map(Provider::name).toList());
        }
        return List.copyOf(primary);
    }

    @Override
    public void destroy() {
        executor.shutdownNow();
    }
}
