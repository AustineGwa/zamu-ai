-- H2 twin of db/migration/V1__shared_jobs.sql, for the local "h2" profile only.
-- Keep the same tables, columns, keys and indexes; only the SQL dialect differs.
-- JSON columns are CLOB here: H2's JSON type would store our JSON text as a quoted JSON string.
CREATE TABLE jobs (
    id               BINARY(16)    NOT NULL,
    type             VARCHAR(64)   NOT NULL,
    tenant_id        BINARY(16)    NULL,
    payload          CLOB          NOT NULL,
    run_at           TIMESTAMP(3)  NOT NULL,
    status           VARCHAR(32)   NOT NULL,
    attempts         INT           NOT NULL DEFAULT 0,
    max_attempts     INT           NOT NULL DEFAULT 5,
    lock_key         VARCHAR(64)   NULL,
    running_lock_key VARCHAR(64)   GENERATED ALWAYS AS (CASE WHEN status = 'running' THEN lock_key END),
    lease_until      TIMESTAMP(3)  NULL,
    last_error       VARCHAR(2000) NULL,
    created_at       TIMESTAMP(3)  NOT NULL,
    updated_at       TIMESTAMP(3)  NOT NULL,
    CONSTRAINT pk_jobs PRIMARY KEY (id),
    CONSTRAINT uk_jobs_running_lock_key UNIQUE (running_lock_key)
);
CREATE INDEX ix_jobs_status_run_at ON jobs (status, run_at);
CREATE INDEX ix_jobs_lock_key_status ON jobs (lock_key, status);
CREATE INDEX ix_jobs_tenant_status ON jobs (tenant_id, status);
CREATE INDEX ix_jobs_status_lease ON jobs (status, lease_until);
