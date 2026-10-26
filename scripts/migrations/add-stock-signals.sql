-- Migration: stock analyzer signal snapshot + owned-stock list.
--
-- stock_signals: one row per (market, sector, stock) holding that stock's CURRENT signal — a
-- point-in-time snapshot, not a log. The scheduled daily job replays each sector's winning strategy
-- over live Yahoo bars for the whole universe (India + US) and fully replaces the table per market
-- (delete-by-market + insert in one txn). Only actionable states (BUY / SELL / HOLD) are stored;
-- FLAT (no position, no signal) is the uninteresting majority and is dropped. The per-sector winning
-- strategy is chosen offline by the stock-analyzer optimizer and shipped as classpath resources
-- (stock/sector_winners[_us].csv, stock/refined_variants_us.json).
--
-- stock_portfolio: the user's owned tickers per market — SELL Slack alerts are limited to these and
-- the app highlights owned rows. Tickers are stored suffix-stripped + uppercased (e.g. RELIANCE).
--
-- Safe to re-run.

CREATE TABLE IF NOT EXISTS stock_signals
(
    market              VARCHAR(4)    NOT NULL,          -- 'in' (NSE) or 'us' (S&P 500)
    sector              VARCHAR(64)   NOT NULL,
    stock               VARCHAR(32)   NOT NULL,
    strategy            VARCHAR(128)  NOT NULL,
    signal_type         VARCHAR(8)    NOT NULL,          -- BUY / SELL / HOLD
    data_date           DATE          NOT NULL,          -- date of the last bar the signal was read from
    price               DECIMAL(18, 4) NOT NULL,
    stop_loss           DECIMAL(18, 4) NULL,
    take_profit         DECIMAL(18, 4) NULL,
    entry_price         DECIMAL(18, 4) NULL,
    entry_date          DATE          NULL,
    unrealized_pnl_pct  DECIMAL(10, 4) NULL,
    actionable_tomorrow TINYINT(1)    NOT NULL DEFAULT 0, -- signal fired on last close, fills next open
    comment             VARCHAR(255)  NULL,
    prev_close          DECIMAL(18, 4) NULL,               -- previous session close (for 1-day change)
    day_change_pct      DECIMAL(12, 4) NULL,               -- last close vs previous close, %
    updated_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    -- One current signal per stock within a market+sector; reads are "one market's rows".
    PRIMARY KEY (market, sector, stock)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS stock_portfolio
(
    market   VARCHAR(4)   NOT NULL,     -- 'in' or 'us'
    stock    VARCHAR(32)  NOT NULL,     -- ticker, suffix-stripped + uppercased (e.g. RELIANCE)
    added_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (market, stock)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
