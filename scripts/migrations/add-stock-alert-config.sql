-- Migration: per-market Slack alert schedule (UI-configurable), replacing the old slack_alerts.json.
-- The alert Lambda (every few minutes) fires each market's alert once/day at hour:minute in the
-- given timezone; last_sent_date guards against duplicates within the fire window.
--
-- Safe to re-run.

CREATE TABLE IF NOT EXISTS stock_alert_config
(
    market        VARCHAR(4)   NOT NULL,     -- 'in' / 'us'
    enabled       TINYINT(1)   NOT NULL DEFAULT 1,
    hour          TINYINT      NOT NULL DEFAULT 17,   -- 0-23, in `timezone`
    minute        TINYINT      NOT NULL DEFAULT 0,    -- 0-59
    timezone      VARCHAR(48)  NOT NULL DEFAULT 'Asia/Kolkata',
    weekdays_only TINYINT(1)   NOT NULL DEFAULT 1,
    last_sent_date DATE        NULL,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (market)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Defaults mirror the old config: IN 17:00 IST, US 09:00 IST, weekdays only.
INSERT INTO stock_alert_config (market, enabled, hour, minute, timezone, weekdays_only) VALUES
    ('in', 1, 17, 0, 'Asia/Kolkata', 1),
    ('us', 1, 9, 0, 'Asia/Kolkata', 1)
ON DUPLICATE KEY UPDATE market = market;   -- no-op if already present
