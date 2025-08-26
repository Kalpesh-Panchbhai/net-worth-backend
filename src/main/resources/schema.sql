CREATE TABLE IF NOT EXISTS users
(
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    external_user_id VARCHAR(100)    NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_external_user_id (external_user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS income_sources
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

CREATE TABLE IF NOT EXISTS income_tags
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

CREATE TABLE IF NOT EXISTS incomes
(
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id          BIGINT UNSIGNED NOT NULL,
    income_source_id BIGINT UNSIGNED NOT NULL,
    income_tag_id    BIGINT UNSIGNED NOT NULL,
    net_amount       DECIMAL(10, 4)  NOT NULL DEFAULT 0,
    tax_paid         DECIMAL(10, 4)  NOT NULL DEFAULT 0,
    currency         CHAR(3)         NOT NULL,
    credited_date    DATETIME        NOT NULL,
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

    KEY idx_incomes_user (user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS accounts
(
    id        BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id   BIGINT UNSIGNED NOT NULL,
    name      VARCHAR(100)    NOT NULL,
    type      VARCHAR(50)     NOT NULL,
    is_active TINYINT         NOT NULL DEFAULT 1,
    currency  CHAR(3)         NOT NULL,
    PRIMARY KEY (id),

    CONSTRAINT fk_accounts_user
        FOREIGN KEY (user_id) REFERENCES users (id)
            ON DELETE CASCADE,

    KEY idx_accounts_user (user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS holdings
(
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    account_id BIGINT UNSIGNED NOT NULL,
    name       VARCHAR(100)    NOT NULL,
    symbol     VARCHAR(50)     NOT NULL,
    units      DECIMAL(10, 4)  NOT NULL DEFAULT 0, -- fractional quantities supported
    PRIMARY KEY (id),

    CONSTRAINT fk_holdings_account
        FOREIGN KEY (account_id) REFERENCES accounts (id)
            ON DELETE CASCADE,

    UNIQUE KEY uk_holdings_account_symbol (account_id, symbol),

    KEY idx_holdings_account (account_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS watchlists
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

CREATE TABLE IF NOT EXISTS watchlist_accounts
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

CREATE TABLE IF NOT EXISTS account_metrics
(
    account_id        BIGINT UNSIGNED NOT NULL,
    invested          DECIMAL(10, 4)  NOT NULL DEFAULT 0,
    current_day_value DECIMAL(10, 4)  NOT NULL DEFAULT 0,
    last_day_value    DECIMAL(10, 4)  NOT NULL DEFAULT 0,
    currency          CHAR(3)         NOT NULL,

    PRIMARY KEY (account_id),

    CONSTRAINT fk_account_metrics_account
        FOREIGN KEY (account_id) REFERENCES accounts (id)
            ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS holding_metrics
(
    holding_id        BIGINT UNSIGNED NOT NULL,
    invested          DECIMAL(10, 4)  NOT NULL DEFAULT 0,
    current_day_value DECIMAL(10, 4)  NOT NULL DEFAULT 0,
    last_day_value    DECIMAL(10, 4)  NOT NULL DEFAULT 0,
    currency          CHAR(3)         NOT NULL,

    PRIMARY KEY (holding_id),

    CONSTRAINT fk_holding_metrics_holding
        FOREIGN KEY (holding_id) REFERENCES holdings (id)
            ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS watchlist_metrics
(
    watchlist_id      BIGINT UNSIGNED NOT NULL,
    invested          DECIMAL(10, 4)  NOT NULL DEFAULT 0,
    current_day_value DECIMAL(10, 4)  NOT NULL DEFAULT 0,
    last_day_value    DECIMAL(10, 4)  NOT NULL DEFAULT 0,
    currency          CHAR(3)         NOT NULL,

    PRIMARY KEY (watchlist_id),

    CONSTRAINT fk_watchlist_metrics_watchlist
        FOREIGN KEY (watchlist_id) REFERENCES watchlists (id)
            ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;