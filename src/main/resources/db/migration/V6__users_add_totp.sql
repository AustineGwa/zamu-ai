-- NFR-08: Zamu operator and admin logins need a second factor (authenticator app, RFC 6238 TOTP).
-- totp_secret is encrypted with ZAMU_ENCRYPTION_KEY (NFR-06). totp_last_step blocks code replay.
ALTER TABLE users
    ADD COLUMN totp_secret     VARBINARY(128) NULL AFTER disabled_at,
    ADD COLUMN totp_enabled_at DATETIME(3)    NULL AFTER totp_secret,
    ADD COLUMN totp_last_step  BIGINT         NULL AFTER totp_enabled_at;
