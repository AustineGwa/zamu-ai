-- H2 twin of db/migration/V2__tenants_create.sql, for the local "h2" profile only.
CREATE TABLE tenants (
    id                   BINARY(16)   NOT NULL,
    name                 VARCHAR(200) NOT NULL,
    display_name         VARCHAR(200) NOT NULL,
    plan                 VARCHAR(32)  NOT NULL,
    status               VARCHAR(32)  NOT NULL,
    timezone             VARCHAR(64)  NOT NULL DEFAULT 'Africa/Nairobi',
    default_language     VARCHAR(8)   NOT NULL DEFAULT 'en',
    tone                 VARCHAR(32)  NOT NULL DEFAULT 'friendly',
    conversation_limit   INT          NOT NULL,
    confidence_threshold DECIMAL(3,2) NOT NULL DEFAULT 0.60,
    created_at           TIMESTAMP(3) NOT NULL,
    updated_at           TIMESTAMP(3) NOT NULL,
    CONSTRAINT pk_tenants PRIMARY KEY (id),
    CONSTRAINT uk_tenants_name UNIQUE (name)
);
CREATE INDEX ix_tenants_status ON tenants (status);
CREATE INDEX ix_tenants_display_name ON tenants (display_name);
