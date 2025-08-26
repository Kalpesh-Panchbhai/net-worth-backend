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