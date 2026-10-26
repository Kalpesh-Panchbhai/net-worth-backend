-- Migration: a tiny global key/value store for app-wide metadata (not per-user).
-- First use: the "last refreshed" timestamp the daily job stamps after each successful run,
-- so the UI can show data freshness. Safe to re-run.

CREATE TABLE IF NOT EXISTS app_meta
(
    meta_key   VARCHAR(64)  NOT NULL PRIMARY KEY,
    -- Free-form value; the last-refresh key stores epoch milliseconds as text.
    meta_value VARCHAR(255) NOT NULL,
    updated_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
