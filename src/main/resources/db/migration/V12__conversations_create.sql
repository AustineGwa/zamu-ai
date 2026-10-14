-- Customers, conversations and messages (spec: Data model; MSG-02, MSG-04, MSG-14, MSG-17). Owned by conversations.
-- Channel-neutral: a conversation has a channel (whatsapp now, voice later), and a customer is identified by
-- phone number. The spec's customers.wa_id is named phone here because voice callers may not use WhatsApp.
CREATE TABLE customers (
    id                 BINARY(16)   NOT NULL,
    tenant_id          BINARY(16)   NOT NULL,
    phone              VARCHAR(16)  NOT NULL,     -- international digits, e.g. 254712345678
    name               VARCHAR(120) NULL,         -- WhatsApp profile name, or as given
    language           VARCHAR(8)   NULL,         -- en, sw
    opted_in_marketing BOOLEAN      NOT NULL DEFAULT FALSE,
    opted_out_at       DATETIME(3)  NULL,         -- MSG-19 "stop": no reminders or marketing
    blocked            BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at         DATETIME(3)  NULL,
    created_at         DATETIME(3)  NOT NULL,
    updated_at         DATETIME(3)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_customers_tenant_phone (tenant_id, phone),
    KEY ix_customers_tenant_name (tenant_id, name),
    CONSTRAINT fk_customers_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE conversations (
    id                       BINARY(16)  NOT NULL,
    tenant_id                BINARY(16)  NOT NULL,
    customer_id              BINARY(16)  NOT NULL,
    channel                  VARCHAR(16) NOT NULL,   -- whatsapp, voice
    status                   VARCHAR(32) NOT NULL,   -- bot, handed_over, closed
    assigned_user_id         BINARY(16)  NULL,
    opened_at                DATETIME(3) NOT NULL,
    last_message_at          DATETIME(3) NOT NULL,
    last_customer_message_at DATETIME(3) NULL,
    window_expires_at        DATETIME(3) NULL,       -- MSG-14: free-form replies allowed until this time
    billable_session_start   DATETIME(3) NULL,       -- MSG-17
    unread_count             INT         NOT NULL DEFAULT 0,
    holding_reply_sent       BOOLEAN     NOT NULL DEFAULT FALSE,   -- MSG-09: once per conversation
    closed_at                DATETIME(3) NULL,
    created_at               DATETIME(3) NOT NULL,
    updated_at               DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    KEY ix_conversations_tenant_status_last (tenant_id, status, last_message_at),
    KEY ix_conversations_tenant_last (tenant_id, last_message_at),
    KEY ix_conversations_tenant_customer (tenant_id, customer_id, channel, opened_at),
    KEY ix_conversations_assigned (tenant_id, assigned_user_id),
    CONSTRAINT fk_conversations_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_conversations_customer FOREIGN KEY (customer_id) REFERENCES customers (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE messages (
    id              BINARY(16)   NOT NULL,
    tenant_id       BINARY(16)   NOT NULL,
    conversation_id BINARY(16)   NOT NULL,
    direction       VARCHAR(8)   NOT NULL,   -- in, out
    sender          VARCHAR(16)  NOT NULL,   -- customer, bot, staff, system
    sender_user_id  BINARY(16)   NULL,       -- the staff member, for staff messages
    wa_message_id   VARCHAR(128) NULL,       -- MSG-02 de-duplication; set on outbound once sent
    type            VARCHAR(16)  NOT NULL,   -- text, interactive, template, media
    body            TEXT         NULL,
    media_ref       VARCHAR(255) NULL,       -- MSG-13: provider media id and kind, never the file itself
    template_json   JSON         NULL,       -- template name, language and parameters
    status          VARCHAR(16)  NOT NULL,   -- received, queued, sent, delivered, read, failed
    error_code      VARCHAR(64)  NULL,
    ai_confidence   DECIMAL(3,2) NULL,
    kb_entry_ids    JSON         NULL,
    created_at      DATETIME(3)  NOT NULL,
    updated_at      DATETIME(3)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_messages_wa_message_id (wa_message_id),
    KEY ix_messages_tenant_conversation_created (tenant_id, conversation_id, created_at),
    KEY ix_messages_tenant_created (tenant_id, created_at),
    CONSTRAINT fk_messages_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_messages_conversation FOREIGN KEY (conversation_id) REFERENCES conversations (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
