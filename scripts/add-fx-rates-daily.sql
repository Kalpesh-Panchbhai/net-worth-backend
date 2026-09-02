-- Migration: daily FX close rates.
--
-- Display-currency conversion used to call Yahoo Finance synchronously on every API request,
-- which added hundreds of milliseconds (sometimes seconds) per page load. Rates now live here,
-- written once a day by the daily data job, so a request only does a small indexed read.
--
-- Safe to re-run.

CREATE TABLE IF NOT EXISTS fx_rates_daily
(
    base_currency  CHAR(3)         NOT NULL,
    quote_currency CHAR(3)         NOT NULL,
    date           DATE            NOT NULL,
    rate           DECIMAL(24, 10) NOT NULL,

    PRIMARY KEY (base_currency, quote_currency, date)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- Identity rows are never stored; a base == quote lookup short-circuits to 1 in code.
-- Backfill is performed by the application (FxRateBackfillService) on the next
-- daily-data run or manual /refresh, deriving every supported pair from USD legs.

-- The "today and yesterday" summary lookups filter on `date` alone; no existing index on
-- account_holding_values_daily leads with it, so those predicates scanned the table.
--
-- MySQL 8 supports IF NOT EXISTS on neither ADD INDEX nor CREATE INDEX, so the index is added
-- through a prepared statement guarded on information_schema: a second run is a no-op instead of
-- error 1061 (duplicate key name).
SET @has_index = (SELECT COUNT(*)
                  FROM information_schema.STATISTICS
                  WHERE TABLE_SCHEMA = DATABASE()
                    AND TABLE_NAME = 'account_holding_values_daily'
                    AND INDEX_NAME = 'idx_ahvd_date_account');

SET @add_index = IF(@has_index = 0,
                    'ALTER TABLE account_holding_values_daily ADD INDEX idx_ahvd_date_account (date, account_id)',
                    'DO 0');

PREPARE add_index FROM @add_index;
EXECUTE add_index;
DEALLOCATE PREPARE add_index;
