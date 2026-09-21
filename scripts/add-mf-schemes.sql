-- Migration: AMFI mutual-fund scheme master.
--
-- Source is AMFI's scheme-data CSV (portal.amfiindia.com/DownloadSchemeData_Po.aspx?mf=0), which
-- unlike NAVAll.txt carries the SEBI category as a real column rather than as stateful section
-- headers. Only Direct + Growth share classes of live open-ended schemes are stored: IDCW share
-- classes drop NAV on payout, so their returns are wrong without a total-return adjustment, and
-- Regular duplicates the same portfolio at a higher expense ratio.
--
-- `plan` and `option` are stored even though the sync currently writes only DIRECT/GROWTH, so
-- widening the ingest later (Regular history is needed for anything beyond ~13.7 years, because
-- Direct plans only exist from 2013-01-01) is a config change rather than a migration.
--
-- Safe to re-run.

CREATE TABLE IF NOT EXISTS mf_schemes
(
    -- AMFI scheme code. Stable, and the join key to NAV history and to mfapi.in.
    scheme_code   INT UNSIGNED NOT NULL,

    isin          VARCHAR(12)  NULL,
    amc           VARCHAR(255) NOT NULL,
    -- Scheme name without the plan/option suffix, for display.
    name          VARCHAR(255) NOT NULL,
    -- AMFI's "Scheme NAV Name": the only field that reliably encodes plan and option.
    nav_name      VARCHAR(255) NOT NULL,

    -- AMFI's category string, kept verbatim so a resolver change can be re-applied without
    -- re-fetching, and so an unexpected value stays auditable.
    raw_category  VARCHAR(255) NOT NULL,
    -- Resolved from raw_category: EQUITY | DEBT | HYBRID | SOLUTION | OTHER | UNKNOWN.
    asset_class   VARCHAR(16)  NOT NULL,
    -- Canonical sub-category ("Large Cap", "Small Cap", ...). AMFI ships the same category under
    -- both pre-2017 and SEBI-2017 spellings (singular/plural too), which would otherwise split one
    -- peer group across several leaderboards. Ranking happens on this column, never raw_category.
    sub_category  VARCHAR(64)  NOT NULL,

    plan          VARCHAR(16)  NOT NULL,
    `option`      VARCHAR(16)  NOT NULL,

    launch_date   DATE         NULL,

    -- Whether the scheme appeared in the most recent NAVAll snapshot. A scheme that stops
    -- reporting (merged, wound up) is kept for history but must drop out of rankings.
    is_live       TINYINT(1)   NOT NULL DEFAULT 1,
    last_nav_date DATE         NULL,

    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (scheme_code),
    -- Screener and category-leader reads both filter (asset_class, sub_category) and then require
    -- is_live, so the index leads with the filter columns.
    INDEX idx_mf_schemes_category (asset_class, sub_category, is_live),
    INDEX idx_mf_schemes_isin (isin)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- AMFI adds and respells categories without notice. An unrecognised value resolves to
-- (UNKNOWN, 'Unclassified') rather than failing the sync, and is recorded here so it surfaces as
-- a fixable gap instead of a silently mis-bucketed fund.
CREATE TABLE IF NOT EXISTS mf_category_unmapped
(
    raw_category VARCHAR(255) NOT NULL,
    scheme_count INT UNSIGNED NOT NULL,
    first_seen   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (raw_category)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
