-- Adds the per-user Insights dashboard layout table so the widget configuration syncs across
-- devices. Idempotent (CREATE TABLE IF NOT EXISTS) — safe to run more than once. Also present in
-- src/main/resources/schema.sql. Run this against the database before deploying the backend change.

CREATE TABLE IF NOT EXISTS insights_layout
(
    user_id     BIGINT UNSIGNED NOT NULL,
    layout_json JSON            NOT NULL,
    updated_at  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id),

    CONSTRAINT fk_insights_layout_user
        FOREIGN KEY (user_id) REFERENCES users (id)
            ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
