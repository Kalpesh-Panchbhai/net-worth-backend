-- Consolidates the Insights dashboard layout into the generic user_config store, so every per-user
-- setting lives in one table. Copies each insights_layout row into user_config under the 'insights'
-- key. Idempotent (ON DUPLICATE KEY UPDATE) — safe to re-run to pick up last-minute writes before
-- the old table is dropped.
--
-- Prerequisite: user_config must already exist (scripts/migrations/add-user-config.sql).
-- After verifying the copy, drop the old table:  DROP TABLE insights_layout;

INSERT INTO user_config (user_id, config_key, config_json)
SELECT user_id, 'insights', layout_json
FROM insights_layout
ON DUPLICATE KEY UPDATE config_json = VALUES(config_json);
