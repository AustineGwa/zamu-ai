-- H2 twin of db/migration/V8__shared_webhook_events.sql, for the local h2 profile only.
CREATE TABLE webhook_events (
    id           BINARY(16)   NOT NULL,
    source       VARCHAR(16)  NOT NULL,
    tenant_id    BINARY(16)   NULL,
    raw_payload  CLOB         NOT NULL,
    received_at  TIMESTAMP(3) NOT NULL,
    processed_at TIMESTAMP(3) NULL,
    CONSTRAINT pk_webhook_events PRIMARY KEY (id)
);
CREATE INDEX ix_webhook_events_source_received ON webhook_events (source, received_at);
CREATE INDEX ix_webhook_events_tenant_received ON webhook_events (tenant_id, received_at);
