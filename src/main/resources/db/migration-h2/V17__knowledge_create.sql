-- H2 twin of db/migration/V17__knowledge_create.sql, for the local h2 profile only.
CREATE TABLE kb_entries (
    id          BINARY(16)   NOT NULL,
    tenant_id   BINARY(16)   NOT NULL,
    title       VARCHAR(200) NOT NULL,
    question    VARCHAR(500) NOT NULL,
    answer_en   CLOB         NOT NULL,
    answer_sw   CLOB         NULL,
    status      VARCHAR(16)  NOT NULL,
    approved_by BINARY(16)   NULL,
    approved_at TIMESTAMP(3) NULL,
    source_ref  VARCHAR(300) NULL,
    created_at  TIMESTAMP(3) NOT NULL,
    updated_at  TIMESTAMP(3) NOT NULL,
    CONSTRAINT pk_kb_entries PRIMARY KEY (id),
    CONSTRAINT fk_kb_entries_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
);
CREATE INDEX ix_kb_entries_tenant_status ON kb_entries (tenant_id, status);

CREATE TABLE kb_chunks (
    id              BINARY(16)   NOT NULL,
    tenant_id       BINARY(16)   NOT NULL,
    kb_entry_id     BINARY(16)   NOT NULL,
    language        VARCHAR(8)   NOT NULL,
    content         CLOB         NOT NULL,
    embedding       BLOB         NULL,
    embedding_model VARCHAR(100) NULL,
    created_at      TIMESTAMP(3) NOT NULL,
    CONSTRAINT pk_kb_chunks PRIMARY KEY (id),
    CONSTRAINT fk_kb_chunks_entry FOREIGN KEY (kb_entry_id) REFERENCES kb_entries (id)
);
CREATE INDEX ix_kb_chunks_tenant ON kb_chunks (tenant_id);
CREATE INDEX ix_kb_chunks_entry ON kb_chunks (kb_entry_id);
