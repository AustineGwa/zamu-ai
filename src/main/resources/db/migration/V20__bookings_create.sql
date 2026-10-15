-- Bookings, services, resources, holidays and the Google Calendar connection (spec: Data model; BK-01 to BK-14).
-- Owned by the bookings package. Times are UTC DATETIME(3); money is DECIMAL(12,2) KES.

CREATE TABLE booking_settings (
    tenant_id          BINARY(16)  NOT NULL,
    min_notice_min     INT         NOT NULL,  -- BK-04, default 120
    max_advance_days   INT         NOT NULL,  -- BK-04, default 30
    change_cutoff_min  INT         NOT NULL,  -- BK-08, default 240
    reminder_2h        BOOLEAN     NOT NULL,  -- BK-11 optional second reminder
    slot_step_min      INT         NOT NULL,  -- ASSUMPTION: slots start every 30 minutes by default
    updated_at         DATETIME(3) NOT NULL,
    PRIMARY KEY (tenant_id),
    CONSTRAINT fk_booking_settings_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE holidays (
    id         BINARY(16)   NOT NULL,
    tenant_id  BINARY(16)   NOT NULL,
    holiday_date DATE       NOT NULL,   -- in the tenant's timezone
    closed     BOOLEAN      NOT NULL,
    note       VARCHAR(200) NULL,
    created_at DATETIME(3)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_holidays_tenant_day (tenant_id, holiday_date),
    CONSTRAINT fk_holidays_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE services (
    id             BINARY(16)    NOT NULL,
    tenant_id      BINARY(16)    NOT NULL,
    name           VARCHAR(120)  NOT NULL,
    name_sw        VARCHAR(120)  NULL,
    description    VARCHAR(500)  NULL,
    duration_min   INT           NOT NULL,
    price_min      DECIMAL(12,2) NULL,
    price_max      DECIMAL(12,2) NULL,
    deposit_amount DECIMAL(12,2) NOT NULL,  -- 0 means no deposit (BK-06)
    buffer_min     INT           NOT NULL,
    active         BOOLEAN       NOT NULL,
    created_at     DATETIME(3)   NOT NULL,
    updated_at     DATETIME(3)   NOT NULL,
    PRIMARY KEY (id),
    KEY ix_services_tenant_active (tenant_id, active),
    CONSTRAINT fk_services_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE resources (
    id          BINARY(16)   NOT NULL,
    tenant_id   BINARY(16)   NOT NULL,
    name        VARCHAR(120) NOT NULL,     -- staff member or room, as customers see it ("Dr Otieno")
    role        VARCHAR(120) NULL,
    calendar_id VARCHAR(255) NULL,         -- optional own Google calendar for free/busy
    active      BOOLEAN      NOT NULL,
    created_at  DATETIME(3)  NOT NULL,
    updated_at  DATETIME(3)  NOT NULL,
    PRIMARY KEY (id),
    KEY ix_resources_tenant_active (tenant_id, active),
    CONSTRAINT fk_resources_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE service_resources (
    tenant_id   BINARY(16) NOT NULL,
    service_id  BINARY(16) NOT NULL,
    resource_id BINARY(16) NOT NULL,
    PRIMARY KEY (service_id, resource_id),
    KEY ix_service_resources_tenant (tenant_id),
    KEY ix_service_resources_resource (resource_id),
    CONSTRAINT fk_service_resources_service FOREIGN KEY (service_id) REFERENCES services (id),
    CONSTRAINT fk_service_resources_resource FOREIGN KEY (resource_id) REFERENCES resources (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE calendar_connections (
    id                  BINARY(16)     NOT NULL,
    tenant_id           BINARY(16)     NOT NULL,
    provider            VARCHAR(16)    NOT NULL,  -- google
    calendar_id         VARCHAR(255)   NOT NULL,  -- "primary" or a calendar's ID
    account_email       VARCHAR(255)   NULL,
    oauth_refresh_token VARBINARY(2048) NOT NULL, -- encrypted (NFR-06)
    status              VARCHAR(16)    NOT NULL,  -- connected, revoked
    last_error          VARCHAR(255)   NULL,
    created_at          DATETIME(3)    NOT NULL,
    updated_at          DATETIME(3)    NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_calendar_connections_tenant (tenant_id),
    CONSTRAINT fk_calendar_connections_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE bookings (
    id                BINARY(16)   NOT NULL,
    tenant_id         BINARY(16)   NOT NULL,
    customer_id       BINARY(16)   NOT NULL,
    customer_name     VARCHAR(120) NULL,      -- BK-14: name, phone and service only
    customer_phone    VARCHAR(20)  NOT NULL,
    conversation_id   BINARY(16)   NULL,
    service_id        BINARY(16)   NOT NULL,
    resource_id       BINARY(16)   NULL,
    starts_at         DATETIME(3)  NOT NULL,
    ends_at           DATETIME(3)  NOT NULL,
    status            VARCHAR(32)  NOT NULL,  -- held, pending_payment, confirmed, cancelled, completed, no_show
    hold_expires_at   DATETIME(3)  NULL,
    calendar_event_id VARCHAR(255) NULL,
    source            VARCHAR(16)  NOT NULL,  -- bot, staff
    notes             VARCHAR(500) NULL,
    awaiting_reply    VARCHAR(16)  NULL,      -- confirmation or reminder: numbered replies refer to this booking
    awaiting_until    DATETIME(3)  NULL,
    cancelled_by      VARCHAR(16)  NULL,      -- customer, staff, system, calendar
    created_at        DATETIME(3)  NOT NULL,
    updated_at        DATETIME(3)  NOT NULL,
    PRIMARY KEY (id),
    KEY ix_bookings_tenant_starts (tenant_id, starts_at),
    KEY ix_bookings_tenant_customer (tenant_id, customer_id),
    KEY ix_bookings_tenant_status (tenant_id, status),
    KEY ix_bookings_status_hold (status, hold_expires_at),
    KEY ix_bookings_service (service_id),
    KEY ix_bookings_resource (resource_id),
    CONSTRAINT fk_bookings_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_bookings_service FOREIGN KEY (service_id) REFERENCES services (id),
    CONSTRAINT fk_bookings_resource FOREIGN KEY (resource_id) REFERENCES resources (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
