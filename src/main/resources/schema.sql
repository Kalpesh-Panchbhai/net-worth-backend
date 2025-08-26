-- Schema for users and income_tags (scaled for 1M+ users)
-- Notes:
-- - users: only internal PK (id) and external identifier (user_id), user_id unique
-- - income_tags: multiple tags per user, name unique per user, no timestamps
-- - Single default per user is enforced by the application (not via DB constraints)
-- - Deleting a user cascades to income_tags; deleting a tag does not affect users

-- Users
CREATE TABLE IF NOT EXISTS users (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id VARCHAR(191) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_users_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;