-- H2 twin of db/migration/V18__ai_evaluation_create.sql, for the local h2 profile only.
CREATE TABLE eval_cases (
    id              BINARY(16)    NOT NULL,
    tenant_id       BINARY(16)    NOT NULL,
    kind            VARCHAR(16)   NOT NULL,
    language        VARCHAR(8)    NOT NULL,
    question        VARCHAR(500)  NOT NULL,
    expected_answer VARCHAR(1000) NULL,
    must_include    VARCHAR(500)  NULL,
    active          BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP(3)  NOT NULL,
    updated_at      TIMESTAMP(3)  NOT NULL,
    CONSTRAINT pk_eval_cases PRIMARY KEY (id),
    CONSTRAINT fk_eval_cases_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
);
CREATE INDEX ix_eval_cases_tenant ON eval_cases (tenant_id, active);

CREATE TABLE eval_runs (
    id            BINARY(16)   NOT NULL,
    tenant_id     BINARY(16)   NOT NULL,
    status        VARCHAR(16)  NOT NULL,
    model         VARCHAR(100) NULL,
    total         INT          NOT NULL DEFAULT 0,
    passed        INT          NOT NULL DEFAULT 0,
    trap_failures INT          NOT NULL DEFAULT 0,
    requested_by  BINARY(16)   NULL,
    created_at    TIMESTAMP(3) NOT NULL,
    finished_at   TIMESTAMP(3) NULL,
    CONSTRAINT pk_eval_runs PRIMARY KEY (id),
    CONSTRAINT fk_eval_runs_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
);
CREATE INDEX ix_eval_runs_tenant_created ON eval_runs (tenant_id, created_at);

CREATE TABLE eval_results (
    id          BINARY(16)   NOT NULL,
    tenant_id   BINARY(16)   NOT NULL,
    run_id      BINARY(16)   NOT NULL,
    case_id     BINARY(16)   NOT NULL,
    reply       CLOB         NULL,
    handed_over BOOLEAN      NOT NULL,
    confidence  DECIMAL(3,2) NULL,
    passed      BOOLEAN      NOT NULL,
    reason      VARCHAR(300) NULL,
    created_at  TIMESTAMP(3) NOT NULL,
    CONSTRAINT pk_eval_results PRIMARY KEY (id),
    CONSTRAINT fk_eval_results_run FOREIGN KEY (run_id) REFERENCES eval_runs (id),
    CONSTRAINT fk_eval_results_case FOREIGN KEY (case_id) REFERENCES eval_cases (id)
);
CREATE INDEX ix_eval_results_run ON eval_results (run_id);
