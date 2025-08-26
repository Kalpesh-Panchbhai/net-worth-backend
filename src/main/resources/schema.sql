-- Schema for users and income_tags (scaled for 1M+ users)
-- Notes:
-- - users: only internal PK (id) and external identifier (external_user_id), external_user_id unique
-- - income_tags: multiple tags per user, name unique per user, no timestamps
-- - Single default per user is enforced by the application (not via DB constraints)
-- - Deleting a user cascades to income_tags; deleting a tag does not affect users

-- Users
CREATE TABLE IF NOT EXISTS users
(
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    external_user_id VARCHAR(100)    NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_external_user_id (external_user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- Income Sources
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
    name       VARCHAR(100)    NOT NULL,
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

    -- Helpful indexes for scale and queries
    KEY idx_incomes_user (user_id),
    KEY idx_incomes_source (income_source_id),
    KEY idx_incomes_tag (income_tag_id),
    KEY idx_incomes_user_date (user_id, credited_date)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;