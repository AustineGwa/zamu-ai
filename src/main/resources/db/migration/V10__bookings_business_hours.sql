-- Opening and handover hours per weekday, in the tenant's timezone (spec: Data model, business_hours; HO-02).
-- Owned by bookings. Holidays arrive with Milestone 5.
CREATE TABLE business_hours (
    id                 BINARY(16)  NOT NULL,
    tenant_id          BINARY(16)  NOT NULL,
    day_of_week        TINYINT     NOT NULL,      -- 1 = Monday ... 7 = Sunday (ISO-8601)
    opens_at           TIME        NULL,          -- NULL = closed that day
    closes_at          TIME        NULL,
    handover_opens_at  TIME        NULL,          -- NULL = no staff for handovers that day
    handover_closes_at TIME        NULL,
    created_at         DATETIME(3) NOT NULL,
    updated_at         DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_business_hours_tenant_day (tenant_id, day_of_week),
    CONSTRAINT fk_business_hours_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
