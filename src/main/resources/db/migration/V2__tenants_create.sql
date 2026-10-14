-- One row per client business (spec: Data model, tenants). Owned by the tenants package.
CREATE TABLE tenants (
    id                   BINARY(16)   NOT NULL,
    name                 VARCHAR(200) NOT NULL,                       -- registered name
    display_name         VARCHAR(200) NOT NULL,                       -- the name customers use
    plan                 VARCHAR(32)  NOT NULL,                       -- starter, growth, business
    status               VARCHAR(32)  NOT NULL,                       -- onboarding, live, staff_only, paused, offboarded
    timezone             VARCHAR(64)  NOT NULL DEFAULT 'Africa/Nairobi',
    default_language     VARCHAR(8)   NOT NULL DEFAULT 'en',          -- en, sw
    tone                 VARCHAR(32)  NOT NULL DEFAULT 'friendly',    -- friendly, formal
    conversation_limit   INT          NOT NULL,
    confidence_threshold DECIMAL(3,2) NOT NULL DEFAULT 0.60,          -- AI-03
    created_at           DATETIME(3)  NOT NULL,
    updated_at           DATETIME(3)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenants_name (name),
    KEY ix_tenants_status (status),
    KEY ix_tenants_display_name (display_name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
