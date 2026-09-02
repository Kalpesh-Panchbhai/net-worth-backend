-- ============================================================================
-- Clone net_worth prod database to net_worth_dev
-- Run: mysql -u root -p < scripts/clone-prod-to-dev.sql
-- ============================================================================

DROP DATABASE IF EXISTS net_worth_dev;
CREATE DATABASE net_worth_dev CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE net_worth_dev;

-- ============================================================================
-- Create tables (same schema as prod)
-- ============================================================================

CREATE TABLE users
(
    id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    external_user_id   VARCHAR(100)    NOT NULL,
    preferred_currency CHAR(3)         NOT NULL DEFAULT 'INR',
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_external_user_id (external_user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE income_sources
(
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id    BIGINT UNSIGNED NOT NULL,
    name       VARCHAR(100)    NOT NULL,
    is_default TINYINT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_income_sources_user
        FOREIGN KEY (user_id) REFERENCES users (id)
            ON DELETE CASCADE,
    UNIQUE KEY uk_income_sources_user_name (user_id, name),
    KEY idx_income_sources_user (user_id),
    KEY idx_income_sources_user_default (user_id, is_default)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE income_tags
(
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id    BIGINT UNSIGNED NOT NULL,
    name       VARCHAR(100)    NOT NULL,
    is_default TINYINT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_income_tags_user
        FOREIGN KEY (user_id) REFERENCES users (id)
            ON DELETE CASCADE,
    UNIQUE KEY uk_income_tags_user_name (user_id, name),
    KEY idx_income_tags_user (user_id),
    KEY idx_income_tags_user_default (user_id, is_default)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE incomes
(
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id          BIGINT UNSIGNED NOT NULL,
    income_source_id BIGINT UNSIGNED NOT NULL,
    income_tag_id    BIGINT UNSIGNED NOT NULL,
    net_amount       DECIMAL(20, 4)  NOT NULL DEFAULT 0,
    tax_paid         DECIMAL(20, 4)  NOT NULL DEFAULT 0,
    currency         CHAR(3)         NOT NULL,
    credited_date    DATE            NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_incomes_user
        FOREIGN KEY (user_id) REFERENCES users (id)
            ON DELETE CASCADE,
    CONSTRAINT fk_incomes_income_source
        FOREIGN KEY (income_source_id) REFERENCES income_sources (id)
            ON DELETE RESTRICT,
    CONSTRAINT fk_incomes_income_tag
        FOREIGN KEY (income_tag_id) REFERENCES income_tags (id)
            ON DELETE RESTRICT,
    UNIQUE KEY uk_incomes_user_date_source_tag (user_id, credited_date, income_source_id, income_tag_id),
    KEY idx_incomes_user (user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE accounts
(
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id          BIGINT UNSIGNED NOT NULL,
    name             VARCHAR(100)    NOT NULL,
    type             VARCHAR(50)     NOT NULL,
    is_active        TINYINT         NOT NULL DEFAULT 1,
    needs_daily_data TINYINT         NOT NULL DEFAULT 0,
    currency         CHAR(3)         NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_accounts_user
        FOREIGN KEY (user_id) REFERENCES users (id)
            ON DELETE CASCADE,
    UNIQUE KEY uk_accounts_user_name_type (user_id, name, type),
    KEY idx_accounts_user (user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE holdings
(
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    account_id BIGINT UNSIGNED NOT NULL,
    name       VARCHAR(100)    NOT NULL,
    symbol     VARCHAR(50)     NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_holdings_account
        FOREIGN KEY (account_id) REFERENCES accounts (id)
            ON DELETE CASCADE,
    UNIQUE KEY uk_holdings_account_symbol (account_id, symbol),
    KEY idx_holdings_account (account_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE transactions
(
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    account_id BIGINT UNSIGNED NOT NULL,
    holding_id BIGINT UNSIGNED NOT NULL,
    txn_date   DATE            NOT NULL,
    invested   DECIMAL(20, 4)  NOT NULL,
    value      DECIMAL(20, 4)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_transactions_account
        FOREIGN KEY (account_id) REFERENCES accounts (id)
            ON DELETE CASCADE,
    CONSTRAINT fk_transactions_holding
        FOREIGN KEY (holding_id) REFERENCES holdings (id)
            ON DELETE CASCADE,
    KEY idx_tx_account_date (account_id, txn_date),
    UNIQUE KEY uk_tx_holding_date (holding_id, txn_date)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE watchlists
(
    id      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    name    VARCHAR(100)    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_watchlists_user
        FOREIGN KEY (user_id) REFERENCES users (id)
            ON DELETE CASCADE,
    UNIQUE KEY uk_watchlists_user_name (user_id, name),
    KEY idx_watchlists_user (user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE watchlist_accounts
(
    watchlist_id BIGINT UNSIGNED NOT NULL,
    account_id   BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (watchlist_id, account_id),
    CONSTRAINT fk_watchlist_accounts_watchlist
        FOREIGN KEY (watchlist_id) REFERENCES watchlists (id)
            ON DELETE CASCADE,
    CONSTRAINT fk_watchlist_accounts_account
        FOREIGN KEY (account_id) REFERENCES accounts (id)
            ON DELETE CASCADE,
    KEY idx_watchlist_accounts_account (account_id),
    KEY idx_watchlist_accounts_watchlist (watchlist_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE account_holding_values_daily
(
    account_id BIGINT UNSIGNED NOT NULL,
    holding_id BIGINT UNSIGNED NOT NULL,
    date       DATE            NOT NULL,
    value      DECIMAL(20, 4)  NOT NULL DEFAULT 0,
    invested   DECIMAL(20, 4)  NOT NULL DEFAULT 0,
    PRIMARY KEY (account_id, holding_id, date),
    CONSTRAINT fk_ahvd_account
        FOREIGN KEY (account_id) REFERENCES accounts (id)
            ON DELETE CASCADE,
    CONSTRAINT fk_ahvd_holding
        FOREIGN KEY (holding_id) REFERENCES holdings (id)
            ON DELETE CASCADE,
    KEY idx_ahvd_account_date (account_id, date),
    KEY idx_ahvd_holding_date (holding_id, date)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- ============================================================================
-- Copy all data from prod to dev (order respects foreign keys)
-- ============================================================================

INSERT INTO net_worth_dev.users SELECT * FROM net_worth.users;
INSERT INTO net_worth_dev.income_sources SELECT * FROM net_worth.income_sources;
INSERT INTO net_worth_dev.income_tags SELECT * FROM net_worth.income_tags;
INSERT INTO net_worth_dev.incomes SELECT * FROM net_worth.incomes;
INSERT INTO net_worth_dev.accounts SELECT * FROM net_worth.accounts;
INSERT INTO net_worth_dev.holdings SELECT * FROM net_worth.holdings;
INSERT INTO net_worth_dev.transactions SELECT * FROM net_worth.transactions;
INSERT INTO net_worth_dev.watchlists SELECT * FROM net_worth.watchlists;
INSERT INTO net_worth_dev.watchlist_accounts SELECT * FROM net_worth.watchlist_accounts;
INSERT INTO net_worth_dev.account_holding_values_daily SELECT * FROM net_worth.account_holding_values_daily;

SELECT 'Clone complete: net_worth → net_worth_dev' AS status;
