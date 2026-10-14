-- H2 twin of db/migration/V14__reports_usage_daily.sql, for the local h2 profile only.
CREATE TABLE usage_daily (
    tenant_id            BINARY(16)    NOT NULL,
    usage_date           DATE          NOT NULL,
    conversations        INT           NOT NULL DEFAULT 0,
    messages_in          INT           NOT NULL DEFAULT 0,
    messages_out         INT           NOT NULL DEFAULT 0,
    ai_tokens_in         BIGINT        NOT NULL DEFAULT 0,
    ai_tokens_out        BIGINT        NOT NULL DEFAULT 0,
    ai_cost_usd          DECIMAL(12,6) NOT NULL DEFAULT 0,
    wa_billable_messages INT           NOT NULL DEFAULT 0,
    CONSTRAINT pk_usage_daily PRIMARY KEY (tenant_id, usage_date),
    CONSTRAINT fk_usage_daily_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
);
CREATE INDEX ix_usage_daily_date ON usage_daily (usage_date);
