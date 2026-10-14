-- Raw inbound webhook payloads, kept 30 days (spec: Data model, webhook_events; MSG-01, PAY-05).
-- The spec lists two owners (whatsapp, payments); one owner is required, so shared.webhooks owns the table and
-- both features write through WebhookEventStore.
CREATE TABLE webhook_events (
    id           BINARY(16)  NOT NULL,
    source       VARCHAR(16) NOT NULL,      -- meta, daraja, google
    tenant_id    BINARY(16)  NULL,          -- NULL when the payload can't be routed to a tenant (MSG-03)
    raw_payload  JSON        NOT NULL,
    received_at  DATETIME(3) NOT NULL,
    processed_at DATETIME(3) NULL,
    PRIMARY KEY (id),
    KEY ix_webhook_events_source_received (source, received_at),
    KEY ix_webhook_events_tenant_received (tenant_id, received_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
