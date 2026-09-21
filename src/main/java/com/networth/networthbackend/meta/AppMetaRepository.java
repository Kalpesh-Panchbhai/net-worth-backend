package com.networth.networthbackend.meta;

import com.networth.networthbackend.db.Database;
import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * A tiny global key/value store for app-wide metadata that isn't tied to a user — currently just
 * the "last refreshed" timestamp the daily job stamps on each successful run.
 */
@Slf4j
public class AppMetaRepository {

    /** Epoch millis of the last successful data refresh (scheduled or manual). */
    public static final String KEY_LAST_REFRESH_AT = "last_refresh_at";

    private static final String T = "app_meta";
    private static final String K = "meta_key";
    private static final String V = "meta_value";

    /** Upserts one key. */
    public void set(String key, String value) throws SQLException {
        String sql = "INSERT INTO " + T + " (" + K + ", " + V + ") VALUES (?, ?)"
                + " ON DUPLICATE KEY UPDATE " + V + " = VALUES(" + V + ")";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        }
    }

    /** Reads one key's value, or null when absent. */
    public String get(String key) throws SQLException {
        String sql = "SELECT " + V + " FROM " + T + " WHERE " + K + " = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    /** Convenience: records "now" as the last successful refresh. Never throws — best-effort. */
    public void stampRefreshNow() {
        try {
            set(KEY_LAST_REFRESH_AT, String.valueOf(System.currentTimeMillis()));
        } catch (SQLException e) {
            log.warn("Could not record last-refresh timestamp: {}", e.getMessage());
        }
    }
}
