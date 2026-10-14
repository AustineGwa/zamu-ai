-- H2 twin of db/migration/V10__bookings_business_hours.sql, for the local h2 profile only.
CREATE TABLE business_hours (
    id                 BINARY(16)   NOT NULL,
    tenant_id          BINARY(16)   NOT NULL,
    day_of_week        TINYINT      NOT NULL,
    opens_at           TIME         NULL,
    closes_at          TIME         NULL,
    handover_opens_at  TIME         NULL,
    handover_closes_at TIME         NULL,
    created_at         TIMESTAMP(3) NOT NULL,
    updated_at         TIMESTAMP(3) NOT NULL,
    CONSTRAINT pk_business_hours PRIMARY KEY (id),
    CONSTRAINT uk_business_hours_tenant_day UNIQUE (tenant_id, day_of_week),
    CONSTRAINT fk_business_hours_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
);
