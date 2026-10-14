-- H2 twin of db/migration/V6__users_add_totp.sql, for the local "h2" profile only.
ALTER TABLE users ADD COLUMN totp_secret VARBINARY(128) NULL;
ALTER TABLE users ADD COLUMN totp_enabled_at TIMESTAMP(3) NULL;
ALTER TABLE users ADD COLUMN totp_last_step BIGINT NULL;
