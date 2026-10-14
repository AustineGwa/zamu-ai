-- H2 twin of db/migration/V11__whatsapp_create.sql, for the local h2 profile only.
CREATE TABLE whatsapp_accounts (
    id               BINARY(16)      NOT NULL,
    tenant_id        BINARY(16)      NOT NULL,
    waba_id          VARCHAR(64)     NOT NULL,
    phone_number_id  VARCHAR(64)     NOT NULL,
    display_phone    VARCHAR(32)     NOT NULL,
    access_token     VARBINARY(2048) NOT NULL,
    webhook_verified BOOLEAN         NOT NULL DEFAULT FALSE,
    quality_rating   VARCHAR(16)     NULL,
    created_at       TIMESTAMP(3)    NOT NULL,
    updated_at       TIMESTAMP(3)    NOT NULL,
    CONSTRAINT pk_whatsapp_accounts PRIMARY KEY (id),
    CONSTRAINT uk_whatsapp_accounts_phone_number_id UNIQUE (phone_number_id),
    CONSTRAINT uk_whatsapp_accounts_tenant UNIQUE (tenant_id),
    CONSTRAINT fk_whatsapp_accounts_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
);

CREATE TABLE wa_templates (
    id          BINARY(16)   NOT NULL,
    tenant_id   BINARY(16)   NOT NULL,
    name        VARCHAR(512) NOT NULL,
    category    VARCHAR(32)  NOT NULL,
    language    VARCHAR(16)  NOT NULL,
    body        CLOB         NOT NULL,
    meta_status VARCHAR(32)  NOT NULL,
    meta_reason VARCHAR(500) NULL,
    created_at  TIMESTAMP(3) NOT NULL,
    updated_at  TIMESTAMP(3) NOT NULL,
    CONSTRAINT pk_wa_templates PRIMARY KEY (id),
    CONSTRAINT uk_wa_templates_tenant_name_language UNIQUE (tenant_id, name, language),
    CONSTRAINT fk_wa_templates_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
);
CREATE INDEX ix_wa_templates_tenant_status ON wa_templates (tenant_id, meta_status);
