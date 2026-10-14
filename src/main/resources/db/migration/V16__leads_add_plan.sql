-- Which plan a website sign-up chose (pricing_plans.code; no foreign key, the leads and pricing packages stay apart).
ALTER TABLE leads ADD COLUMN plan_code VARCHAR(32) NULL AFTER message;
