-- MSG-09: the configured holding reply sent once per conversation in staff-only or paused mode.
-- NULL means the default wording in code.
ALTER TABLE tenants ADD COLUMN holding_reply VARCHAR(500) NULL AFTER confidence_threshold;
