-- H2 twin of db/migration/V7__leads_create.sql, for the local h2 profile only.
CREATE TABLE leads (
    id            BINARY(16)    NOT NULL,
    business_name VARCHAR(200)  NOT NULL,
    contact_name  VARCHAR(120)  NOT NULL,
    phone         VARCHAR(16)   NOT NULL,      -- normalised, 2547XXXXXXXX or 2541XXXXXXXX
    email         VARCHAR(254)  NULL,
    business_type VARCHAR(32)   NOT NULL,      -- clinic, salon, school, other
    message       VARCHAR(1000) NULL,
    status        VARCHAR(32)   NOT NULL,      -- new, contacted, won, lost
    consent_at    TIMESTAMP(3)   NOT NULL,      -- when they agreed to be contacted
    created_at    TIMESTAMP(3)   NOT NULL,
    updated_at    TIMESTAMP(3)   NOT NULL,
    CONSTRAINT pk_leads PRIMARY KEY (id)
);
CREATE INDEX ix_leads_status_created ON leads (status, created_at);
CREATE INDEX ix_leads_created ON leads (created_at);
