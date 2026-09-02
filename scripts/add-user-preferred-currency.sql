-- Migration: add per-user preferred display currency.
-- Existing rows default to INR. Run against both prod and dev databases.
ALTER TABLE users
    ADD COLUMN preferred_currency CHAR(3) NOT NULL DEFAULT 'INR' AFTER external_user_id;
