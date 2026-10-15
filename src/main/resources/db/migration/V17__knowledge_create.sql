-- Knowledge base (spec: Data model, kb_entries and kb_chunks; AI-01, AI-12). Owned by the knowledge package.
-- Only approved entries are chunked; chunks are rebuilt by the reembed_kb_entry job.
-- ASSUMPTION: keyword search runs in the application over the cached chunks (same results on MySQL and H2), so
-- the spec's FULLTEXT index is not needed in version 1.
CREATE TABLE kb_entries (
    id          BINARY(16)   NOT NULL,
    tenant_id   BINARY(16)   NOT NULL,
    title       VARCHAR(200) NOT NULL,
    question    VARCHAR(500) NOT NULL,      -- how customers ask it
    answer_en   TEXT         NOT NULL,
    answer_sw   TEXT         NULL,
    status      VARCHAR(16)  NOT NULL,      -- draft, approved, archived
    approved_by BINARY(16)   NULL,
    approved_at DATETIME(3)  NULL,
    source_ref  VARCHAR(300) NULL,          -- where the fact came from, e.g. "price list 2026-09"
    created_at  DATETIME(3)  NOT NULL,
    updated_at  DATETIME(3)  NOT NULL,
    PRIMARY KEY (id),
    KEY ix_kb_entries_tenant_status (tenant_id, status),
    CONSTRAINT fk_kb_entries_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE kb_chunks (
    id              BINARY(16)   NOT NULL,
    tenant_id       BINARY(16)   NOT NULL,
    kb_entry_id     BINARY(16)   NOT NULL,
    language        VARCHAR(8)   NOT NULL,  -- en, sw
    content         TEXT         NOT NULL,
    embedding       MEDIUMBLOB   NULL,      -- float32 array, little-endian; null when no embedding provider is set
    embedding_model VARCHAR(100) NULL,
    created_at      DATETIME(3)  NOT NULL,
    PRIMARY KEY (id),
    KEY ix_kb_chunks_tenant (tenant_id),
    KEY ix_kb_chunks_entry (kb_entry_id),
    CONSTRAINT fk_kb_chunks_entry FOREIGN KEY (kb_entry_id) REFERENCES kb_entries (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
