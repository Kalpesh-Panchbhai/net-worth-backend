-- Migration: precomputed mutual-fund metrics and category ranks.
--
-- Why precompute at all, given the maths is cheap? Measured on this dataset: computing every
-- metric for all 1,761 funds takes ~1.1s of CPU, but FETCHING their 2.87M NAV rows takes ~128s.
-- Ranking one 35-fund category means reading 86,599 NAV rows (~6-9s); reading the same ranking out
-- of mf_rank is 35 rows. The win is in not moving NAV data per request, not in saving arithmetic.
--
-- Shape is deliberately long/narrow (one row per metric rather than one column per metric) so that
-- adding a metric later is an INSERT, not an ALTER on a table with millions of rows. That is the
-- extension point for the analyses that come after this one.
--
-- Safe to re-run.

CREATE TABLE IF NOT EXISTS mf_metric
(
    scheme_code INT UNSIGNED NOT NULL,
    -- NAV date the metrics were computed against, not the date the job ran: it makes a recompute
    -- idempotent and lets a stale row be spotted.
    as_of       DATE         NOT NULL,
    -- '1Y','3Y','5Y','7Y','10Y','SI' (since inception), '1M','3M','6M'. 'SI' is not a duration, so
    -- this is a label rather than a number of months.
    horizon     VARCHAR(8)   NOT NULL,
    -- 'cagr', 'absolute', 'rolling_avg', 'rolling_worst', 'rolling_best', 'rolling_share_negative',
    -- 'rolling_share_above_12', 'rolling_windows', 'volatility', 'sharpe', 'sortino',
    -- 'max_drawdown', 'current_drawdown'. Free-form on purpose — see the file header.
    metric      VARCHAR(32)  NOT NULL,
    -- DOUBLE, not DECIMAL: every value here is a ratio or a count feeding a percentage displayed to
    -- two decimals. DECIMAL's exactness buys nothing and costs space and arithmetic speed.
    value       DOUBLE       NOT NULL,

    PRIMARY KEY (scheme_code, as_of, horizon, metric)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- Ranks live separately from metrics because they are a property of a peer group, not of a fund:
-- one fund's rank changes when a sibling's NAV moves. Keeping them apart means a category can be
-- re-ranked without rewriting per-fund metric rows.
CREATE TABLE IF NOT EXISTS mf_rank
(
    -- The canonical sub-category from MfCategoryResolver, never AMFI's raw string: the raw values
    -- carry pre-2017 and SEBI-2017 spellings of the same category, which would split one peer group
    -- into several leaderboards.
    sub_category VARCHAR(64)  NOT NULL,
    as_of        DATE         NOT NULL,
    horizon      VARCHAR(8)   NOT NULL,
    metric       VARCHAR(32)  NOT NULL,
    scheme_code  INT UNSIGNED NOT NULL,

    -- 1 = best. Direction is per-metric: high is better for returns, low is better for volatility
    -- and drawdown, so the ranking job owns the sort order rather than the reader.
    rank_in_category SMALLINT UNSIGNED NOT NULL,
    -- 0..100, where 100 is best. Comparable across categories of different sizes, which a raw rank
    -- is not: 5th of 5 and 5th of 256 mean opposite things.
    percentile       DECIMAL(5, 2)     NOT NULL,
    -- Peer-group size at computation time, so a "rank 3" can be read honestly ("3 of 5").
    peer_count       SMALLINT UNSIGNED NOT NULL,

    -- Keyed on scheme_code rather than rank so a re-rank upserts cleanly; the index below serves
    -- the ordered read.
    PRIMARY KEY (sub_category, as_of, horizon, metric, scheme_code),
    -- "top N of this category on this metric" — the category-leaders query.
    INDEX idx_mf_rank_leaders (sub_category, as_of, horizon, metric, rank_in_category),
    -- "where does this fund sit in its peer group" — the fund-detail query.
    INDEX idx_mf_rank_scheme (scheme_code, as_of, horizon, metric)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- Sizing note: ~1,760 funds x ~40 (horizon, metric) pairs is ~70k metric rows per as_of date.
-- Storing that every day would add ~25M rows a year for data nobody reads, so the recompute job
-- keeps the latest as_of and month-end snapshots only. as_of stays in the primary key so that
-- history remains possible without a migration.
