-- Milestone 7: change requests (spec: Data model, change_requests; owned by tenants), report deliveries (reports)
-- and retention settings (privacy; NFR-11).

CREATE TABLE change_requests (
    id           BINARY(16)   NOT NULL,
    tenant_id    BINARY(16)   NOT NULL,
    requested_by BINARY(16)   NULL,      -- user ID; NULL when the user was later deleted
    type         VARCHAR(16)  NOT NULL,  -- kb, hours, service, other
    details      TEXT         NOT NULL,
    status       VARCHAR(16)  NOT NULL,  -- open, done, declined
    handled_by   BINARY(16)   NULL,
    handled_at   DATETIME(3)  NULL,
    note         VARCHAR(500) NULL,      -- reply to the owner when closing
    created_at   DATETIME(3)  NOT NULL,
    updated_at   DATETIME(3)  NOT NULL,
    PRIMARY KEY (id),
    KEY ix_change_requests_tenant_status (tenant_id, status),
    KEY ix_change_requests_status_created (status, created_at),
    CONSTRAINT fk_change_requests_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- ASSUMPTION: records that a scheduled report or alert went out, so it is sent once per period even with several
-- instances or a restart (weekly report REP-01; running-cost alert NFR-19).
CREATE TABLE report_deliveries (
    tenant_id    BINARY(16)  NOT NULL,
    kind         VARCHAR(32) NOT NULL,  -- weekly_report, cost_alert
    period_start DATE        NOT NULL,  -- Monday of the week, or first day of the month
    sent_at      DATETIME(3) NOT NULL,
    PRIMARY KEY (tenant_id, kind, period_start),
    CONSTRAINT fk_report_deliveries_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- NFR-11: retention per tenant. No row means the defaults (12 months; 30 days after offboarding).
CREATE TABLE retention_settings (
    tenant_id           BINARY(16)  NOT NULL,
    conversation_months INT         NOT NULL,
    offboard_purge_days INT         NOT NULL,
    updated_at          DATETIME(3) NOT NULL,
    PRIMARY KEY (tenant_id),
    CONSTRAINT fk_retention_settings_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
