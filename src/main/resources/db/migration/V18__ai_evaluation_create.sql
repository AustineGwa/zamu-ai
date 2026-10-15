-- Evaluation set per tenant (spec: AI answering, Evaluation set; routes /admin/tenants/{id}/eval). Owned by ai.
-- ASSUMPTION: the spec names the evaluation set but no tables; these hold the cases, each run and each answer.
CREATE TABLE eval_cases (
    id              BINARY(16)    NOT NULL,
    tenant_id       BINARY(16)    NOT NULL,
    kind            VARCHAR(16)   NOT NULL,   -- answer, unknown, medical, injection
    language        VARCHAR(8)    NOT NULL,   -- en, sw
    question        VARCHAR(500)  NOT NULL,
    expected_answer VARCHAR(1000) NULL,       -- shown next to the reply for review
    must_include    VARCHAR(500)  NULL,       -- facts the reply must contain, separated by ";"
    active          BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at      DATETIME(3)   NOT NULL,
    updated_at      DATETIME(3)   NOT NULL,
    PRIMARY KEY (id),
    KEY ix_eval_cases_tenant (tenant_id, active),
    CONSTRAINT fk_eval_cases_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE eval_runs (
    id            BINARY(16)  NOT NULL,
    tenant_id     BINARY(16)  NOT NULL,
    status        VARCHAR(16) NOT NULL,       -- queued, running, done, failed
    model         VARCHAR(100) NULL,
    total         INT         NOT NULL DEFAULT 0,
    passed        INT         NOT NULL DEFAULT 0,
    trap_failures INT         NOT NULL DEFAULT 0,
    requested_by  BINARY(16)  NULL,
    created_at    DATETIME(3) NOT NULL,
    finished_at   DATETIME(3) NULL,
    PRIMARY KEY (id),
    KEY ix_eval_runs_tenant_created (tenant_id, created_at),
    CONSTRAINT fk_eval_runs_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE eval_results (
    id          BINARY(16)   NOT NULL,
    tenant_id   BINARY(16)   NOT NULL,
    run_id      BINARY(16)   NOT NULL,
    case_id     BINARY(16)   NOT NULL,
    reply       TEXT         NULL,
    handed_over BOOLEAN      NOT NULL,
    confidence  DECIMAL(3,2) NULL,
    passed      BOOLEAN      NOT NULL,
    reason      VARCHAR(300) NULL,
    created_at  DATETIME(3)  NOT NULL,
    PRIMARY KEY (id),
    KEY ix_eval_results_run (run_id),
    CONSTRAINT fk_eval_results_run FOREIGN KEY (run_id) REFERENCES eval_runs (id),
    CONSTRAINT fk_eval_results_case FOREIGN KEY (case_id) REFERENCES eval_cases (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
