-- H2 twin of db/migration/V13__handovers_create.sql, for the local h2 profile only.
CREATE TABLE handovers (
    id                   BINARY(16)   NOT NULL,
    tenant_id            BINARY(16)   NOT NULL,
    conversation_id      BINARY(16)   NOT NULL,
    reason               VARCHAR(32)  NOT NULL,
    requested_at         TIMESTAMP(3) NOT NULL,
    accepted_by          BINARY(16)   NULL,
    accepted_at          TIMESTAMP(3) NULL,
    first_staff_reply_at TIMESTAMP(3) NULL,
    escalated_at         TIMESTAMP(3) NULL,
    resolved_at          TIMESTAMP(3) NULL,
    created_at           TIMESTAMP(3) NOT NULL,
    updated_at           TIMESTAMP(3) NOT NULL,
    CONSTRAINT pk_handovers PRIMARY KEY (id),
    CONSTRAINT fk_handovers_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
);
CREATE INDEX ix_handovers_tenant_open ON handovers (tenant_id, resolved_at, requested_at);
CREATE INDEX ix_handovers_tenant_conversation ON handovers (tenant_id, conversation_id);
CREATE INDEX ix_handovers_escalation ON handovers (resolved_at, escalated_at, first_staff_reply_at, requested_at);
