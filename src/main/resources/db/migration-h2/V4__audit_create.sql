-- H2 twin of db/migration/V4__audit_create.sql, for the local "h2" profile only.
CREATE TABLE audit_logs (
    id            BINARY(16)   NOT NULL,
    tenant_id     BINARY(16)   NULL,
    actor_user_id BINARY(16)   NULL,
    action        VARCHAR(64)  NOT NULL,
    entity        VARCHAR(64)  NOT NULL,
    entity_id     BINARY(16)   NULL,
    before_state  CLOB         NULL,
    after_state   CLOB         NULL,
    ip            VARCHAR(45)  NULL,
    created_at    TIMESTAMP(3) NOT NULL,
    CONSTRAINT pk_audit_logs PRIMARY KEY (id)
);
CREATE INDEX ix_audit_tenant_created ON audit_logs (tenant_id, created_at);
CREATE INDEX ix_audit_actor_created ON audit_logs (actor_user_id, created_at);
CREATE INDEX ix_audit_entity ON audit_logs (entity, entity_id);
CREATE INDEX ix_audit_created ON audit_logs (created_at);
