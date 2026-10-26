-- Migration: mutual-fund NAV history.
--
-- One row per (scheme, date). Full history from inception rather than a trailing window, because
-- rolling returns need far more history than their window: 10 years of 5-year rolling windows needs
-- 15 years of NAV. Point-to-point CAGR is the number investors misread; the rolling distribution is
-- the one worth showing, and it is only computable with the whole series.
--
-- Sized for the Direct+Growth scope (~1,760 schemes). Direct plans only exist from 2013-01-01, so
-- this caps at ~13.7 years and ~3.2M rows (~200 MB) regardless of how old the underlying scheme is.
--
-- Safe to re-run.

CREATE TABLE IF NOT EXISTS mf_nav
(
    scheme_code INT UNSIGNED  NOT NULL,
    nav_date    DATE          NOT NULL,
    nav         DECIMAL(14, 4) NOT NULL,

    -- Clustered on (scheme_code, nav_date): every read is "one fund's series over a date range",
    -- which this makes a sequential scan of adjacent rows. No secondary index is needed, and
    -- adding one would only slow the bulk insert.
    PRIMARY KEY (scheme_code, nav_date)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- Backfill progress, so an interrupted run resumes instead of re-fetching ~1,760 schemes.
-- MySQL 8 supports IF NOT EXISTS on neither ADD COLUMN nor CREATE INDEX, so the column is added
-- through a prepared statement guarded on information_schema: a second run is a no-op rather than
-- error 1060 (duplicate column name).
SET @has_column = (SELECT COUNT(*)
                   FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE()
                     AND TABLE_NAME = 'mf_schemes'
                     AND COLUMN_NAME = 'nav_backfilled_at');

SET @add_column = IF(@has_column = 0,
                     'ALTER TABLE mf_schemes ADD COLUMN nav_backfilled_at TIMESTAMP NULL DEFAULT NULL',
                     'DO 0');

PREPARE add_column FROM @add_column;
EXECUTE add_column;
DEALLOCATE PREPARE add_column;
