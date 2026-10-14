-- Daily usage per tenant for billing and margin (spec: Data model, usage_daily; MSG-17, AI-11). Owned by reports.
-- The spec's "date" column is named usage_date because DATE is a reserved word in H2 (the local profile).
CREATE TABLE usage_daily (
    tenant_id            BINARY(16)    NOT NULL,
    usage_date           DATE          NOT NULL,      -- Africa/Nairobi calendar day
    conversations        INT           NOT NULL DEFAULT 0,
    messages_in          INT           NOT NULL DEFAULT 0,
    messages_out         INT           NOT NULL DEFAULT 0,
    ai_tokens_in         BIGINT        NOT NULL DEFAULT 0,
    ai_tokens_out        BIGINT        NOT NULL DEFAULT 0,
    ai_cost_usd          DECIMAL(12,6) NOT NULL DEFAULT 0,
    wa_billable_messages INT           NOT NULL DEFAULT 0,
    PRIMARY KEY (tenant_id, usage_date),
    KEY ix_usage_daily_date (usage_date),
    CONSTRAINT fk_usage_daily_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
