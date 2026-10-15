-- Per-tenant safety settings for the AI step (spec: AI-04 emergency reply, HO-01 "always hand over" topics).
ALTER TABLE tenants ADD COLUMN emergency_number VARCHAR(100) NULL AFTER holding_reply;
ALTER TABLE tenants ADD COLUMN handover_topics VARCHAR(1000) NULL AFTER emergency_number;  -- words or phrases, one per line
