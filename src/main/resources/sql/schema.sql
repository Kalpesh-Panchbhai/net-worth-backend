-- Users: minimal schema with unique external user_id
CREATE TABLE IF NOT EXISTS users (
                                     id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                                     user_id VARCHAR(128) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_user_id (user_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Income Tags: name unique per user; cascade on user delete
CREATE TABLE IF NOT EXISTS income_tags (
                                           id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                                           user_id BIGINT UNSIGNED NOT NULL,             -- FK to users.id
                                           name VARCHAR(100) NOT NULL,
    is_default TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_income_tags_user_name (user_id, name),
    KEY idx_income_tags_user_id (user_id),
    CONSTRAINT fk_income_tags_user
    FOREIGN KEY (user_id) REFERENCES users(id)
    ON DELETE CASCADE
    ON UPDATE RESTRICT
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;