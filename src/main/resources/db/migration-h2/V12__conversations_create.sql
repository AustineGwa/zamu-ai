-- H2 twin of db/migration/V12__conversations_create.sql, for the local h2 profile only.
CREATE TABLE customers (
    id                 BINARY(16)   NOT NULL,
    tenant_id          BINARY(16)   NOT NULL,
    phone              VARCHAR(16)  NOT NULL,
    name               VARCHAR(120) NULL,
    language           VARCHAR(8)   NULL,
    opted_in_marketing BOOLEAN      NOT NULL DEFAULT FALSE,
    opted_out_at       TIMESTAMP(3) NULL,
    blocked            BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at         TIMESTAMP(3) NULL,
    created_at         TIMESTAMP(3) NOT NULL,
    updated_at         TIMESTAMP(3) NOT NULL,
    CONSTRAINT pk_customers PRIMARY KEY (id),
    CONSTRAINT uk_customers_tenant_phone UNIQUE (tenant_id, phone),
    CONSTRAINT fk_customers_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
);
CREATE INDEX ix_customers_tenant_name ON customers (tenant_id, name);

CREATE TABLE conversations (
    id                       BINARY(16)   NOT NULL,
    tenant_id                BINARY(16)   NOT NULL,
    customer_id              BINARY(16)   NOT NULL,
    channel                  VARCHAR(16)  NOT NULL,
    status                   VARCHAR(32)  NOT NULL,
    assigned_user_id         BINARY(16)   NULL,
    opened_at                TIMESTAMP(3) NOT NULL,
    last_message_at          TIMESTAMP(3) NOT NULL,
    last_customer_message_at TIMESTAMP(3) NULL,
    window_expires_at        TIMESTAMP(3) NULL,
    billable_session_start   TIMESTAMP(3) NULL,
    unread_count             INT          NOT NULL DEFAULT 0,
    holding_reply_sent       BOOLEAN      NOT NULL DEFAULT FALSE,
    closed_at                TIMESTAMP(3) NULL,
    created_at               TIMESTAMP(3) NOT NULL,
    updated_at               TIMESTAMP(3) NOT NULL,
    CONSTRAINT pk_conversations PRIMARY KEY (id),
    CONSTRAINT fk_conversations_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_conversations_customer FOREIGN KEY (customer_id) REFERENCES customers (id)
);
CREATE INDEX ix_conversations_tenant_status_last ON conversations (tenant_id, status, last_message_at);
CREATE INDEX ix_conversations_tenant_last ON conversations (tenant_id, last_message_at);
CREATE INDEX ix_conversations_tenant_customer ON conversations (tenant_id, customer_id, channel, opened_at);
CREATE INDEX ix_conversations_assigned ON conversations (tenant_id, assigned_user_id);

CREATE TABLE messages (
    id              BINARY(16)   NOT NULL,
    tenant_id       BINARY(16)   NOT NULL,
    conversation_id BINARY(16)   NOT NULL,
    direction       VARCHAR(8)   NOT NULL,
    sender          VARCHAR(16)  NOT NULL,
    sender_user_id  BINARY(16)   NULL,
    wa_message_id   VARCHAR(128) NULL,
    type            VARCHAR(16)  NOT NULL,
    body            CLOB         NULL,
    media_ref       VARCHAR(255) NULL,
    template_json   CLOB         NULL,
    status          VARCHAR(16)  NOT NULL,
    error_code      VARCHAR(64)  NULL,
    ai_confidence   DECIMAL(3,2) NULL,
    kb_entry_ids    CLOB         NULL,
    created_at      TIMESTAMP(3) NOT NULL,
    updated_at      TIMESTAMP(3) NOT NULL,
    CONSTRAINT pk_messages PRIMARY KEY (id),
    CONSTRAINT uk_messages_wa_message_id UNIQUE (wa_message_id),
    CONSTRAINT fk_messages_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_messages_conversation FOREIGN KEY (conversation_id) REFERENCES conversations (id)
);
CREATE INDEX ix_messages_tenant_conversation_created ON messages (tenant_id, conversation_id, created_at);
CREATE INDEX ix_messages_tenant_created ON messages (tenant_id, created_at);
