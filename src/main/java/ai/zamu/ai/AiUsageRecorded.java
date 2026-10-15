package ai.zamu.ai;

import java.math.BigDecimal;
import java.time.Instant;

import ai.zamu.shared.ids.TenantId;

/** AI-11: tokens and cost of one model call, for usage_daily (the reports package listens). */
public record AiUsageRecorded(TenantId tenantId, String model, long tokensIn, long tokensOut, BigDecimal costUsd,
        Instant at) {
}
