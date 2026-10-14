-- H2 twin of db/migration/V9__tenants_add_holding_reply.sql, for the local h2 profile only.
ALTER TABLE tenants ADD COLUMN holding_reply VARCHAR(500) NULL;
