-- H2 twin of db/migration/V16__leads_add_plan.sql, for the local h2 profile only.
ALTER TABLE leads ADD COLUMN plan_code VARCHAR(32) NULL;
