-- Sign-ups from the public website (leads package). Prospects, not tenant data, so there is no tenant_id.
-- ASSUMPTION: not in the specification; added for the lead generation page. Retention is not decided yet.
CREATE TABLE leads (
    id            BINARY(16)    NOT NULL,
    business_name VARCHAR(200)  NOT NULL,
    contact_name  VARCHAR(120)  NOT NULL,
    phone         VARCHAR(16)   NOT NULL,      -- normalised, 2547XXXXXXXX or 2541XXXXXXXX
    email         VARCHAR(254)  NULL,
    business_type VARCHAR(32)   NOT NULL,      -- clinic, salon, school, other
    message       VARCHAR(1000) NULL,
    status        VARCHAR(32)   NOT NULL,      -- new, contacted, won, lost
    consent_at    DATETIME(3)   NOT NULL,      -- when they agreed to be contacted
    created_at    DATETIME(3)   NOT NULL,
    updated_at    DATETIME(3)   NOT NULL,
    PRIMARY KEY (id),
    KEY ix_leads_status_created (status, created_at),
    KEY ix_leads_created (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

