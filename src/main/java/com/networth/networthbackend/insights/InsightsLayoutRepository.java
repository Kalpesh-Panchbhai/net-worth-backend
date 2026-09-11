package com.networth.networthbackend.insights;

import com.networth.networthbackend.common.JdbcRepository;

import java.sql.SQLException;
import java.util.Optional;

/**
 * Stores one row per user: the JSON layout of their Insights dashboard, so the configuration
 * follows them across devices. The layout is opaque to the backend — it is persisted and returned
 * verbatim as a JSON document ({@code layout_json}).
 */
public class InsightsLayoutRepository extends JdbcRepository<String> {

    public Optional<String> findByUserId(long userId) throws SQLException {
        return queryOne(
                "SELECT layout_json FROM insights_layout WHERE user_id = ?",
                ps -> ps.setLong(1, userId),
                rs -> rs.getString("layout_json"));
    }

    /** Insert or replace the user's layout in a single statement (user_id is the primary key). */
    public void upsert(long userId, String layoutJson) throws SQLException {
        execute(
                "INSERT INTO insights_layout (user_id, layout_json) VALUES (?, ?) "
                        + "ON DUPLICATE KEY UPDATE layout_json = VALUES(layout_json)",
                ps -> {
                    ps.setLong(1, userId);
                    ps.setString(2, layoutJson);
                });
    }
}
