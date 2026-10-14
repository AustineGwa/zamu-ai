-- WhatsApp Cloud API connection and message templates per tenant (spec: Data model; MSG-14, HO-07).
CREATE TABLE whatsapp_accounts (
    id               BINARY(16)     NOT NULL,
    tenant_id        BINARY(16)     NOT NULL,
    waba_id          VARCHAR(64)    NOT NULL,
    phone_number_id  VARCHAR(64)    NOT NULL,
    display_phone    VARCHAR(32)    NOT NULL,
    access_token     VARBINARY(2048) NOT NULL,     -- AES-GCM encrypted (NFR-06), never shown after saving
    webhook_verified BOOLEAN        NOT NULL DEFAULT FALSE,
    quality_rating   VARCHAR(16)    NULL,
    created_at       DATETIME(3)    NOT NULL,
    updated_at       DATETIME(3)    NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_whatsapp_accounts_phone_number_id (phone_number_id),
    UNIQUE KEY uk_whatsapp_accounts_tenant (tenant_id),   -- one number per tenant in version 1
    CONSTRAINT fk_whatsapp_accounts_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE wa_templates (
    id          BINARY(16)   NOT NULL,
    tenant_id   BINARY(16)   NOT NULL,
    name        VARCHAR(512) NOT NULL,      -- Meta template name: lower case, digits, underscores
    category    VARCHAR(32)  NOT NULL,      -- utility, marketing
    language    VARCHAR(16)  NOT NULL,      -- en, sw
    body        TEXT         NOT NULL,      -- with {{1}}, {{2}} placeholders
    meta_status VARCHAR(32)  NOT NULL,      -- draft, pending, approved, rejected
    meta_reason VARCHAR(500) NULL,
    created_at  DATETIME(3)  NOT NULL,
    updated_at  DATETIME(3)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_wa_templates_tenant_name_language (tenant_id, name, language),
    KEY ix_wa_templates_tenant_status (tenant_id, meta_status),
    CONSTRAINT fk_wa_templates_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
