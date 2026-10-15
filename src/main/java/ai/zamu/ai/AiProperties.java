package ai.zamu.ai;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * zamu.ai settings (spec: AI-09 small and large model, AI-10 timeout and backup provider, AI-11 prices).
 *
 * @param prices USD per million tokens, by model name
 */
@ConfigurationProperties("zamu.ai")
record AiProperties(String modelSmall, String modelLarge, Duration timeout, String backupProvider,
        int maxRepliesPerCustomerHour, Map<String, Price> prices) {

    AiProperties {
        timeout = timeout == null ? Duration.ofSeconds(10) : timeout;
        prices = prices == null ? Map.of() : Map.copyOf(prices);
        maxRepliesPerCustomerHour = maxRepliesPerCustomerHour <= 0 ? 30 : maxRepliesPerCustomerHour;
    }

    record Price(BigDecimal input, BigDecimal output) {
    }

    String model(ModelTier tier) {
        return tier == ModelTier.LARGE ? modelLarge : modelSmall;
    }

    /** AI-11: cost of one call in USD; 0 for models without a configured price. */
    BigDecimal cost(String model, long tokensIn, long tokensOut) {
        Price price = model == null ? null : prices.get(model);
        if (price == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal million = BigDecimal.valueOf(1_000_000);
        return price.input().multiply(BigDecimal.valueOf(tokensIn)).divide(million, 6, java.math.RoundingMode.HALF_UP)
                .add(price.output().multiply(BigDecimal.valueOf(tokensOut)).divide(million, 6, java.math.RoundingMode.HALF_UP));
    }
}
