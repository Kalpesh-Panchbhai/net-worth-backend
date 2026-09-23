-- Migration: stock analyzer analytics — positions (my portfolio), backtest trade history, and
-- per-stock backtest performance. These back the Portfolio, Winners, Sector and Stock pages ported
-- from the stock-analyzer UI. Populated from the Oracle-VM report artifacts by StockDataImporter
-- (my_portfolio.json, all_trades.csv, per_stock_performance.csv), per market.
--
-- Safe to re-run.

-- My Portfolio: real positions I hold/held (open or closed), sourced from my_portfolio.json and
-- editable in the app. Open positions drive the Slack SELL filter and the "owned" highlight.
CREATE TABLE IF NOT EXISTS stock_positions
(
    id          VARCHAR(24)   NOT NULL,          -- stable id (from source, or generated)
    market      VARCHAR(4)    NOT NULL,          -- 'in' / 'us'
    stock       VARCHAR(32)   NOT NULL,          -- ticker WITH yahoo suffix as stored on VM (e.g. RELIANCE.NS)
    sector      VARCHAR(64)   NULL,
    strategy    VARCHAR(128)  NULL,
    entry_date  DATE          NULL,
    entry_price DECIMAL(18, 4) NULL,
    quantity    DECIMAL(18, 4) NULL,
    notes       VARCHAR(255)  NULL,
    status      VARCHAR(8)    NOT NULL DEFAULT 'open',  -- open / closed
    exit_price  DECIMAL(18, 4) NULL,
    exit_date   DATE          NULL,
    updated_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_positions_market_status ON stock_positions (market, status);

-- Backtest trade history: every historical trade each sector-winning strategy would have taken.
CREATE TABLE IF NOT EXISTS stock_trades
(
    market      VARCHAR(4)    NOT NULL,
    sector      VARCHAR(64)   NOT NULL,
    strategy    VARCHAR(128)  NULL,
    stock       VARCHAR(32)   NOT NULL,
    trade_num   INT           NOT NULL,
    entry_date  DATE          NULL,
    exit_date   DATE          NULL,
    entry_price DECIMAL(18, 4) NULL,
    exit_price  DECIMAL(18, 4) NULL,
    pnl         DECIMAL(20, 4) NULL,
    pnl_pct     DECIMAL(12, 4) NULL,
    mae_pct     DECIMAL(12, 4) NULL,
    mfe_pct     DECIMAL(12, 4) NULL,
    bars_held   INT           NULL,
    comment     VARCHAR(64)   NULL,
    PRIMARY KEY (market, stock, trade_num)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_trades_market_sector ON stock_trades (market, sector);

-- Per-stock backtest performance summary (one row per stock) + per-trade averages derived from
-- stock_trades at import time so the Signals/Sector pages need no live aggregation.
CREATE TABLE IF NOT EXISTS stock_performance
(
    market        VARCHAR(4)    NOT NULL,
    sector        VARCHAR(64)   NOT NULL,
    strategy      VARCHAR(128)  NULL,
    stock         VARCHAR(32)   NOT NULL,
    cagr          DECIMAL(12, 4) NULL,
    mdd           DECIMAL(12, 4) NULL,
    wr            DECIMAL(12, 4) NULL,
    pf            DECIMAL(12, 4) NULL,
    trades        INT           NULL,
    winners       INT           NULL,
    losers        INT           NULL,
    net_profit    DECIMAL(20, 4) NULL,
    final_equity  DECIMAL(20, 4) NULL,
    sharpe        DECIMAL(12, 4) NULL,
    avg_bars_held DECIMAL(12, 2) NULL,
    avg_trade_pct DECIMAL(12, 4) NULL,
    avg_mae_pct   DECIMAL(12, 4) NULL,
    avg_mfe_pct   DECIMAL(12, 4) NULL,
    avg_hold_days DECIMAL(12, 2) NULL,
    PRIMARY KEY (market, stock)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX idx_perf_market_sector ON stock_performance (market, sector);
