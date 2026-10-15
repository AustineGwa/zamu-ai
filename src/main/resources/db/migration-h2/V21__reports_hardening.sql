-- H2 twin of db/migration/V21__reports_hardening.sql, for the local h2 profile only.
CREATE TABLE change_requests (
    id           BINARY(16)   NOT NULL,
    tenant_id    BINARY(16)   NOT NULL,
    requested_by BINARY(16)   NULL,
    type         VARCHAR(16)  NOT NULL,
    details      CLOB         NOT NULL,
    status       VARCHAR(16)  NOT NULL,
    handled_by   BINARY(16)   NULL,
    handled_at   TIMESTAMP(3) NULL,
    note         VARCHAR(500) NULL,
    created_at   TIMESTAMP(3) NOT NULL,
    updated_at   TIMESTAMP(3) NOT NULL,
    CONSTRAINT pk_change_requests PRIMARY KEY (id),
    CONSTRAINT fk_change_requests_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
);
CREATE INDEX ix_change_requests_tenant_status ON change_requests (tenant_id, status);
CREATE INDEX ix_change_requests_status_created ON change_requests (status, created_at);

CREATE TABLE report_deliveries (
    tenant_id    BINARY(16)   NOT NULL,
    kind         VARCHAR(32)  NOT NULL,
    period_start DATE         NOT NULL,
    sent_at      TIMESTAMP(3) NOT NULL,
    CONSTRAINT pk_report_deliveries PRIMARY KEY (tenant_id, kind, period_start),
    CONSTRAINT fk_report_deliveries_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
);

CREATE TABLE retention_settings (
    tenant_id           BINARY(16)   NOT NULL,
    conversation_months INT          NOT NULL,
    offboard_purge_days INT          NOT NULL,
    updated_at          TIMESTAMP(3) NOT NULL,
    CONSTRAINT pk_retention_settings PRIMARY KEY (tenant_id),
    CONSTRAINT fk_retention_settings_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
);
