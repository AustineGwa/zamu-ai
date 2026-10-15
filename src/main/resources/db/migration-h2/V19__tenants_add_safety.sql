-- H2 twin of db/migration/V19__tenants_add_safety.sql, for the local h2 profile only.
ALTER TABLE tenants ADD COLUMN emergency_number VARCHAR(100) NULL;
ALTER TABLE tenants ADD COLUMN handover_topics VARCHAR(1000) NULL;
