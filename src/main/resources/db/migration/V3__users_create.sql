-- Dashboard and console logins, and one-time login codes. Owned by the users package.
CREATE TABLE users (
    id            BINARY(16)   NOT NULL,
    tenant_id     BINARY(16)   NULL,          -- NULL only for Zamu operator and admin
    email         VARCHAR(254) NOT NULL,      -- stored lower-case; login is by email alone, so unique
    name          VARCHAR(120) NOT NULL,
    role          VARCHAR(32)  NOT NULL,      -- staff, owner, operator, admin
    last_login_at DATETIME(3)  NULL,
    disabled_at   DATETIME(3)  NULL,
    created_at    DATETIME(3)  NOT NULL,
    updated_at    DATETIME(3)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_email (email),
    KEY ix_users_tenant_role (tenant_id, role),
    KEY ix_users_tenant_name (tenant_id, name),
    CONSTRAINT fk_users_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Not tenant-owned: a code is requested before we know who the user is. Purged after 24 hours.
CREATE TABLE login_codes (
    id          BINARY(16)       NOT NULL,
    email       VARCHAR(254)     NOT NULL,
    code_hash   VARBINARY(32)    NOT NULL,    -- HMAC-SHA256(code, ZAMU_CODE_HASH_KEY)
    expires_at  DATETIME(3)      NOT NULL,
    attempts    TINYINT UNSIGNED NOT NULL DEFAULT 0,
    consumed_at DATETIME(3)      NULL,
    created_at  DATETIME(3)      NOT NULL,
    PRIMARY KEY (id),
    KEY ix_login_codes_email_created (email, created_at),
    KEY ix_login_codes_created (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
