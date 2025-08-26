-- Schema for users and income_tags (scaled for 1M+ users)
-- Notes:
-- - users: only internal PK (id) and external identifier (user_id), user_id unique
-- - income_tags: multiple tags per user, name unique per user, no timestamps
-- - Single default per user is enforced by the application (not via DB constraints)
-- - Deleting a user cascades to income_tags; deleting a tag does not affect users

-- Users
CREATE TABLE IF NOT EXISTS users
(
    id      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id VARCHAR(191)    NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_user_id (user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- Incomes
-- Notes:
-- - Deleting a user cascades to incomes (per requirement)
-- - Source and tag are NOT NULL; deleting a referenced source/tag is RESTRICTED (must handle incomes first)
CREATE TABLE IF NOT EXISTS incomes
(
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id          BIGINT UNSIGNED NOT NULL,
    income_source_id BIGINT UNSIGNED NOT NULL,
    income_tag_id    BIGINT UNSIGNED NOT NULL,
    net_amount       DECIMAL         NOT NULL,
    tax_paid         DECIMAL         NOT NULL DEFAULT 0,
    currency         VARCHAR(16)     NOT NULL,
    credited_date    TIMESTAMP       NOT NULL,
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

    -- Helpful indexes for scale and queries
    KEY idx_incomes_user (user_id),
    KEY idx_incomes_source (income_source_id),
    KEY idx_incomes_tag (income_tag_id),
    KEY idx_incomes_user_date (user_id, credited_date)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- Income Sources
CREATE TABLE IF NOT EXISTS income_sources
(
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id    BIGINT UNSIGNED NOT NULL,
    name       VARCHAR(191)    NOT NULL,
    is_default TINYINT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),

    CONSTRAINT fk_income_sources_user
        FOREIGN KEY (user_id) REFERENCES users (id)
            ON DELETE CASCADE,

    -- Prevent duplicate source names per user
    UNIQUE KEY uk_income_sources_user_name (user_id, name),

    -- Helpful indexes for scale
    KEY idx_income_sources_user (user_id),
    KEY idx_income_sources_user_default (user_id, is_default)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- Income Tags
CREATE TABLE IF NOT EXISTS income_tags
(
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id    BIGINT UNSIGNED NOT NULL,
    name       VARCHAR(191)    NOT NULL,
    is_default TINYINT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),

    CONSTRAINT fk_income_tags_user
        FOREIGN KEY (user_id) REFERENCES users (id)
            ON DELETE CASCADE,

    -- Prevent duplicate tag names per user
    UNIQUE KEY uk_income_tags_user_name (user_id, name),

    -- Helpful indexes for scale
    KEY idx_income_tags_user (user_id),
    KEY idx_income_tags_user_default (user_id, is_default)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- Accounts (per-user)
-- Notes:
-- - Each account belongs to exactly one user
-- - (user_id, id) UNIQUE to support composite FKs from join table and enforce tenant scoping
CREATE TABLE IF NOT EXISTS accounts
(
    id       BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id  BIGINT UNSIGNED NOT NULL,
    name     VARCHAR(191)    NOT NULL,
    type     VARCHAR(64)     NOT NULL,
    currency CHAR(16)        NOT NULL,
    PRIMARY KEY (id),

    CONSTRAINT fk_accounts_user
        FOREIGN KEY (user_id) REFERENCES users (id)
            ON DELETE CASCADE,

    -- Optional uniqueness per user to avoid duplicate account names
    UNIQUE KEY uk_accounts_user_name_type (user_id, name, type),
    -- Required to support composite FK from join table (user_id, account_id)
    UNIQUE KEY uk_accounts_user_id (user_id, id),

    -- Helpful indexes
    KEY idx_accounts_user (user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- Watchlists (per-user)
-- Notes:
-- - Each watchlist belongs to exactly one user
-- - (user_id, id) UNIQUE to support composite FKs from join table and enforce tenant scoping
CREATE TABLE IF NOT EXISTS watchlists
(
    id      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id BIGINT UNSIGNED NOT NULL,
    name    VARCHAR(191)    NOT NULL,
    PRIMARY KEY (id),

    CONSTRAINT fk_watchlists_user
        FOREIGN KEY (user_id) REFERENCES users (id)
            ON DELETE CASCADE,

    -- Prevent duplicate watchlist names per user
    UNIQUE KEY uk_watchlists_user_name (user_id, name),
    -- Required to support composite FK from join table (user_id, watchlist_id)
    UNIQUE KEY uk_watchlists_user_id (user_id, id),

    -- Helpful indexes
    KEY idx_watchlists_user (user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- Join table: many-to-many between watchlists and accounts (within same user)
-- Notes:
-- - Composite PK prevents duplicates
-- - Composite FKs enforce that both account and watchlist belong to the same user
CREATE TABLE IF NOT EXISTS watchlist_accounts
(
    user_id      BIGINT UNSIGNED NOT NULL,
    watchlist_id BIGINT UNSIGNED NOT NULL,
    account_id   BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (user_id, watchlist_id, account_id),

    -- Scope by user
    CONSTRAINT fk_wa_user
        FOREIGN KEY (user_id) REFERENCES users (id)
            ON DELETE CASCADE,

    -- Ensure watchlist belongs to same user
    CONSTRAINT fk_wa_watchlist
        FOREIGN KEY (user_id, watchlist_id)
            REFERENCES watchlists (user_id, id)
            ON DELETE CASCADE,

    -- Ensure account belongs to same user
    CONSTRAINT fk_wa_account
        FOREIGN KEY (user_id, account_id)
            REFERENCES accounts (user_id, id)
            ON DELETE CASCADE,

    -- Helpful indexes for lookups in both directions
    KEY idx_wa_user_watchlist (user_id, watchlist_id),
    KEY idx_wa_user_account (user_id, account_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;