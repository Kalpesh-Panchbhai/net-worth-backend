package com.networth.networthbackend.userconfig;

import com.networth.networthbackend.common.JdbcRepository;

import java.sql.SQLException;
import java.util.Optional;

/**
 * A generic per-user key/value store: one JSON document per {@code (user_id, config_key)}. The value
 * is opaque to the backend — persisted and returned verbatim as {@code config_json} — so new client
 * settings (goals, FIRE assumptions, Simulator inputs, …) need no backend change beyond a new key.
 */
public class UserConfigRepository extends JdbcRepository<String> {

    public Optional<String> find(long userId, String key) throws SQLException {
        return queryOne(
                "SELECT config_json FROM user_config WHERE user_id = ? AND config_key = ?",
                ps -> {
                    ps.setLong(1, userId);
                    ps.setString(2, key);
                },
                rs -> rs.getString("config_json"));
    }

    /** Insert or replace this user's value for the key in one statement ((user_id, config_key) is the PK). */
    public void upsert(long userId, String key, String configJson) throws SQLException {
        execute(
                "INSERT INTO user_config (user_id, config_key, config_json) VALUES (?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE config_json = VALUES(config_json)",
                ps -> {
                    ps.setLong(1, userId);
                    ps.setString(2, key);
                    ps.setString(3, configJson);
                });
    }
}
