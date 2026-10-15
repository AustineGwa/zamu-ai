-- H2 twin of db/migration/V20__bookings_create.sql, for the local h2 profile only.
CREATE TABLE booking_settings (
    tenant_id          BINARY(16)  NOT NULL,
    min_notice_min     INT         NOT NULL,
    max_advance_days   INT         NOT NULL,
    change_cutoff_min  INT         NOT NULL,
    reminder_2h        BOOLEAN     NOT NULL,
    slot_step_min      INT         NOT NULL,
    updated_at         TIMESTAMP(3) NOT NULL,
    CONSTRAINT pk_booking_settings PRIMARY KEY (tenant_id),
    CONSTRAINT fk_booking_settings_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
);

CREATE TABLE holidays (
    id         BINARY(16)   NOT NULL,
    tenant_id  BINARY(16)   NOT NULL,
    holiday_date DATE       NOT NULL,
    closed     BOOLEAN      NOT NULL,
    note       VARCHAR(200) NULL,
    created_at TIMESTAMP(3)  NOT NULL,
    CONSTRAINT pk_holidays PRIMARY KEY (id),
    CONSTRAINT fk_holidays_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
);
CREATE UNIQUE INDEX uk_holidays_tenant_day ON holidays (tenant_id, holiday_date);

CREATE TABLE services (
    id             BINARY(16)    NOT NULL,
    tenant_id      BINARY(16)    NOT NULL,
    name           VARCHAR(120)  NOT NULL,
    name_sw        VARCHAR(120)  NULL,
    description    VARCHAR(500)  NULL,
    duration_min   INT           NOT NULL,
    price_min      DECIMAL(12,2) NULL,
    price_max      DECIMAL(12,2) NULL,
    deposit_amount DECIMAL(12,2) NOT NULL,
    buffer_min     INT           NOT NULL,
    active         BOOLEAN       NOT NULL,
    created_at     TIMESTAMP(3)   NOT NULL,
    updated_at     TIMESTAMP(3)   NOT NULL,
    CONSTRAINT pk_services PRIMARY KEY (id),
    CONSTRAINT fk_services_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
);
CREATE INDEX ix_services_tenant_active ON services (tenant_id, active);

CREATE TABLE resources (
    id          BINARY(16)   NOT NULL,
    tenant_id   BINARY(16)   NOT NULL,
    name        VARCHAR(120) NOT NULL,
    role        VARCHAR(120) NULL,
    calendar_id VARCHAR(255) NULL,
    active      BOOLEAN      NOT NULL,
    created_at  TIMESTAMP(3)  NOT NULL,
    updated_at  TIMESTAMP(3)  NOT NULL,
    CONSTRAINT pk_resources PRIMARY KEY (id),
    CONSTRAINT fk_resources_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
);
CREATE INDEX ix_resources_tenant_active ON resources (tenant_id, active);

CREATE TABLE service_resources (
    tenant_id   BINARY(16) NOT NULL,
    service_id  BINARY(16) NOT NULL,
    resource_id BINARY(16) NOT NULL,
    CONSTRAINT pk_service_resources PRIMARY KEY (service_id, resource_id),
    CONSTRAINT fk_service_resources_service FOREIGN KEY (service_id) REFERENCES services (id),
    CONSTRAINT fk_service_resources_resource FOREIGN KEY (resource_id) REFERENCES resources (id)
);
CREATE INDEX ix_service_resources_tenant ON service_resources (tenant_id);
CREATE INDEX ix_service_resources_resource ON service_resources (resource_id);

CREATE TABLE calendar_connections (
    id                  BINARY(16)     NOT NULL,
    tenant_id           BINARY(16)     NOT NULL,
    provider            VARCHAR(16)    NOT NULL,
    calendar_id         VARCHAR(255)   NOT NULL,
    account_email       VARCHAR(255)   NULL,
    oauth_refresh_token VARBINARY(2048) NOT NULL,
    status              VARCHAR(16)    NOT NULL,
    last_error          VARCHAR(255)   NULL,
    created_at          TIMESTAMP(3)    NOT NULL,
    updated_at          TIMESTAMP(3)    NOT NULL,
    CONSTRAINT pk_calendar_connections PRIMARY KEY (id),
    CONSTRAINT fk_calendar_connections_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
);
CREATE UNIQUE INDEX uk_calendar_connections_tenant ON calendar_connections (tenant_id);

CREATE TABLE bookings (
    id                BINARY(16)   NOT NULL,
    tenant_id         BINARY(16)   NOT NULL,
    customer_id       BINARY(16)   NOT NULL,
    customer_name     VARCHAR(120) NULL,
    customer_phone    VARCHAR(20)  NOT NULL,
    conversation_id   BINARY(16)   NULL,
    service_id        BINARY(16)   NOT NULL,
    resource_id       BINARY(16)   NULL,
    starts_at         TIMESTAMP(3)  NOT NULL,
    ends_at           TIMESTAMP(3)  NOT NULL,
    status            VARCHAR(32)  NOT NULL,
    hold_expires_at   TIMESTAMP(3)  NULL,
    calendar_event_id VARCHAR(255) NULL,
    source            VARCHAR(16)  NOT NULL,
    notes             VARCHAR(500) NULL,
    awaiting_reply    VARCHAR(16)  NULL,
    awaiting_until    TIMESTAMP(3)  NULL,
    cancelled_by      VARCHAR(16)  NULL,
    created_at        TIMESTAMP(3)  NOT NULL,
    updated_at        TIMESTAMP(3)  NOT NULL,
    CONSTRAINT pk_bookings PRIMARY KEY (id),
    CONSTRAINT fk_bookings_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id),
    CONSTRAINT fk_bookings_service FOREIGN KEY (service_id) REFERENCES services (id),
    CONSTRAINT fk_bookings_resource FOREIGN KEY (resource_id) REFERENCES resources (id)
);
CREATE INDEX ix_bookings_tenant_starts ON bookings (tenant_id, starts_at);
CREATE INDEX ix_bookings_tenant_customer ON bookings (tenant_id, customer_id);
CREATE INDEX ix_bookings_tenant_status ON bookings (tenant_id, status);
CREATE INDEX ix_bookings_status_hold ON bookings (status, hold_expires_at);
CREATE INDEX ix_bookings_service ON bookings (service_id);
CREATE INDEX ix_bookings_resource ON bookings (resource_id);
