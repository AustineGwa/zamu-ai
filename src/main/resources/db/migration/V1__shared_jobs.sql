-- Background jobs without Redis (spec: Background jobs without Redis; NFR-04; MSG-05).
-- Owned by shared.jobs. tenant_id is NULL for system-wide jobs; there is deliberately no foreign key
-- to tenants, because shared depends on no feature.
CREATE TABLE jobs (
    id               BINARY(16)    NOT NULL,
    type             VARCHAR(64)   NOT NULL,
    tenant_id        BINARY(16)    NULL,
    payload          JSON          NOT NULL,
    run_at           DATETIME(3)   NOT NULL,
    status           VARCHAR(32)   NOT NULL,          -- queued, running, done, failed (waiting to retry), dead
    attempts         INT           NOT NULL DEFAULT 0,
    max_attempts     INT           NOT NULL DEFAULT 5,
    lock_key         VARCHAR(64)   NULL,
    -- MSG-05: at most one running job per lock_key, enforced by the database even across instances.
    running_lock_key VARCHAR(64) AS (CASE WHEN status = 'running' THEN lock_key END) STORED,
    lease_until      DATETIME(3)   NULL,
    last_error       VARCHAR(2000) NULL,
    created_at       DATETIME(3)   NOT NULL,
    updated_at       DATETIME(3)   NOT NULL,
    PRIMARY KEY (id),
    KEY ix_jobs_status_run_at (status, run_at),
    KEY ix_jobs_lock_key_status (lock_key, status),
    KEY ix_jobs_tenant_status (tenant_id, status),
    KEY ix_jobs_status_lease (status, lease_until),
    UNIQUE KEY uk_jobs_running_lock_key (running_lock_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
