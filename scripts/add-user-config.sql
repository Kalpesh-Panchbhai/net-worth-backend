-- Adds a generic per-user configuration store so client-side settings (the Insights dashboard
-- layout, goals, FIRE assumptions, the What-If Simulator inputs, and anything added later) sync
-- across devices. One row per (user_id, config_key); the value is an opaque JSON document persisted
-- and returned verbatim.
-- Idempotent (CREATE TABLE IF NOT EXISTS) — safe to run more than once. Also present in
-- src/main/resources/schema.sql. Run this against the database before deploying the backend change.

CREATE TABLE IF NOT EXISTS user_config
(
    user_id     BIGINT UNSIGNED NOT NULL,
    config_key  VARCHAR(64)     NOT NULL,
    config_json JSON            NOT NULL,
    updated_at  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, config_key),

    CONSTRAINT fk_user_config_user
        FOREIGN KEY (user_id) REFERENCES users (id)
            ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
