-- Append-only audit log (NFR-14). Owned by the audit package.
-- Columns before/after from the spec are reserved words in MySQL, so they are named before_state/after_state.
-- tenant_id is added so owners can view their own tenant's log (spec: Permissions, "Own tenant").
CREATE TABLE audit_logs (
    id            BINARY(16)  NOT NULL,
    tenant_id     BINARY(16)  NULL,
    actor_user_id BINARY(16)  NULL,
    action        VARCHAR(64) NOT NULL,
    entity        VARCHAR(64) NOT NULL,
    entity_id     BINARY(16)  NULL,
    before_state  JSON        NULL,
    after_state   JSON        NULL,
    ip            VARCHAR(45) NULL,
    created_at    DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    KEY ix_audit_tenant_created (tenant_id, created_at),
    KEY ix_audit_actor_created (actor_user_id, created_at),
    KEY ix_audit_entity (entity, entity_id),
    KEY ix_audit_created (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
