package com.networth.networthbackend.stock;

import com.networth.networthbackend.db.Database;
import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reads/writes {@code stock_alert_config} — the per-market Slack alert schedule. */
@Slf4j
public class StockAlertConfigRepository {

    private static final String T = "stock_alert_config";
    private static final String COLS = "market, enabled, hour, minute, timezone, weekdays_only, last_sent_date";

    /** Sensible defaults matching the old slack_alerts.json (IN 17:00, US 09:00, IST, weekdays). */
    private static final Map<String, StockAlertConfig> DEFAULTS = new LinkedHashMap<>();
    static {
        DEFAULTS.put("in", new StockAlertConfig("in", true, 17, 0, "Asia/Kolkata", true, null));
        DEFAULTS.put("us", new StockAlertConfig("us", true, 9, 0, "Asia/Kolkata", true, null));
    }

    /** Returns both markets' configs, inserting defaults for any missing market. */
    public List<StockAlertConfig> findAllEnsuringDefaults() throws SQLException {
        Map<String, StockAlertConfig> byMarket = new LinkedHashMap<>();
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT " + COLS + " FROM " + T);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) byMarket.put(rs.getString("market"), map(rs));
        }
        for (var e : DEFAULTS.entrySet()) {
            if (!byMarket.containsKey(e.getKey())) {
                upsert(e.getValue());
                byMarket.put(e.getKey(), e.getValue());
            }
        }
        return List.copyOf(byMarket.values());
    }

    public void upsert(StockAlertConfig cfg) throws SQLException {
        String sql = "INSERT INTO " + T + " (" + COLS + ") VALUES (?,?,?,?,?,?,?) "
                + "ON DUPLICATE KEY UPDATE enabled=VALUES(enabled), hour=VALUES(hour), minute=VALUES(minute), "
                + "timezone=VALUES(timezone), weekdays_only=VALUES(weekdays_only)";
        try (Connection c = Database.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, cfg.market());
            ps.setInt(2, cfg.enabled() ? 1 : 0);
            ps.setInt(3, cfg.hour());
            ps.setInt(4, cfg.minute());
            ps.setString(5, cfg.timezone());
            ps.setInt(6, cfg.weekdaysOnly() ? 1 : 0);
            ps.setDate(7, cfg.lastSentDate() == null ? null : Date.valueOf(cfg.lastSentDate()));
            ps.executeUpdate();
        }
    }

    /** Stamps the last-sent date so the same day's alert isn't re-sent within the trigger window. */
    public void markSent(String market, LocalDate date) throws SQLException {
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement("UPDATE " + T + " SET last_sent_date = ? WHERE market = ?")) {
            ps.setDate(1, Date.valueOf(date));
            ps.setString(2, market);
            ps.executeUpdate();
        }
    }

    private static StockAlertConfig map(ResultSet rs) throws SQLException {
        Date d = rs.getDate("last_sent_date");
        return new StockAlertConfig(rs.getString("market"), rs.getInt("enabled") == 1, rs.getInt("hour"),
                rs.getInt("minute"), rs.getString("timezone"), rs.getInt("weekdays_only") == 1,
                d == null ? null : d.toLocalDate());
    }
}
