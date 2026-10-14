-- Handovers from Zamu to staff (spec: Data model; HO-01 to HO-07). Owned by handovers.
-- first_staff_reply_at and escalated_at support HO-06 (30-minute escalation) and the median first-reply report.
CREATE TABLE handovers (
    id                   BINARY(16)  NOT NULL,
    tenant_id            BINARY(16)  NOT NULL,
    conversation_id      BINARY(16)  NOT NULL,
    reason               VARCHAR(32) NOT NULL,   -- requested, sensitive, low_confidence, payment_issue, error, staff_only
    requested_at         DATETIME(3) NOT NULL,
    accepted_by          BINARY(16)  NULL,
    accepted_at          DATETIME(3) NULL,
    first_staff_reply_at DATETIME(3) NULL,
    escalated_at         DATETIME(3) NULL,
    resolved_at          DATETIME(3) NULL,
    created_at           DATETIME(3) NOT NULL,
    updated_at           DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    KEY ix_handovers_tenant_open (tenant_id, resolved_at, requested_at),
    KEY ix_handovers_tenant_conversation (tenant_id, conversation_id),
    KEY ix_handovers_escalation (resolved_at, escalated_at, first_staff_reply_at, requested_at),
    CONSTRAINT fk_handovers_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
