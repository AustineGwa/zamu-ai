-- H2 twin of db/migration/V3__users_create.sql, for the local "h2" profile only.
CREATE TABLE users (
    id            BINARY(16)   NOT NULL,
    tenant_id     BINARY(16)   NULL,
    email         VARCHAR(254) NOT NULL,
    name          VARCHAR(120) NOT NULL,
    role          VARCHAR(32)  NOT NULL,
    last_login_at TIMESTAMP(3) NULL,
    disabled_at   TIMESTAMP(3) NULL,
    created_at    TIMESTAMP(3) NOT NULL,
    updated_at    TIMESTAMP(3) NOT NULL,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT fk_users_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
);
CREATE INDEX ix_users_tenant_role ON users (tenant_id, role);
CREATE INDEX ix_users_tenant_name ON users (tenant_id, name);

CREATE TABLE login_codes (
    id          BINARY(16)    NOT NULL,
    email       VARCHAR(254)  NOT NULL,
    code_hash   VARBINARY(32) NOT NULL,
    expires_at  TIMESTAMP(3)  NOT NULL,
    attempts    SMALLINT      NOT NULL DEFAULT 0,
    consumed_at TIMESTAMP(3)  NULL,
    created_at  TIMESTAMP(3)  NOT NULL,
    CONSTRAINT pk_login_codes PRIMARY KEY (id)
);
CREATE INDEX ix_login_codes_email_created ON login_codes (email, created_at);
CREATE INDEX ix_login_codes_created ON login_codes (created_at);
