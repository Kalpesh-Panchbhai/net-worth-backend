package com.networth.networthbackend.stock;

import com.networth.networthbackend.db.Database;
import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Reads and writes {@code stock_signals}: one current signal per (market, sector, stock). */
@Slf4j
public class StockSignalRepository {

    private static final String T = "stock_signals";

    private static final String COLS =
            "market, sector, stock, strategy, signal_type, data_date, price, stop_loss, take_profit, "
            + "entry_price, entry_date, unrealized_pnl_pct, actionable_tomorrow, comment, prev_close, day_change_pct";

    private static final String SQL_INSERT = "INSERT INTO " + T + " (" + COLS + ") "
            + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

    /**
     * Replaces the whole signal snapshot for one market in one transaction: the scan is a full
     * re-computation, so per market the table is a point-in-time snapshot rather than a log. Scoped
     * to the market so refreshing IN never touches US rows and vice versa. Rolls back on failure so
     * a partial scan never half-replaces yesterday's good data.
     *
     * @return rows written
     */
    public int replaceForMarket(String market, List<StockSignal> signals) throws SQLException {
        if (signals == null || signals.isEmpty()) return 0;
        try (Connection conn = Database.getConnection()) {
            boolean autoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            boolean committed = false;
            try (PreparedStatement del = conn.prepareStatement("DELETE FROM " + T + " WHERE market = ?");
                 PreparedStatement ins = conn.prepareStatement(SQL_INSERT)) {
                del.setString(1, market);
                del.executeUpdate();
                for (StockSignal s : signals) {
                    bind(ins, s);
                    ins.addBatch();
                }
                int written = 0;
                for (int r : ins.executeBatch()) {
                    if (r >= 0 || r == PreparedStatement.SUCCESS_NO_INFO) written++;
                }
                conn.commit();
                committed = true;
                return written;
            } finally {
                if (!committed) {
                    try {
                        conn.rollback();
                    } catch (SQLException e) {
                        log.warn("stock_signals rollback failed: {}", e.getMessage());
                    }
                }
                try {
                    conn.setAutoCommit(autoCommit);
                } catch (SQLException e) {
                    log.warn("Restoring autoCommit failed: {}", e.getMessage());
                }
            }
        }
    }

    private static void bind(PreparedStatement ps, StockSignal s) throws SQLException {
        ps.setString(1, s.market());
        ps.setString(2, s.sector());
        ps.setString(3, s.stock());
        ps.setString(4, s.strategy());
        ps.setString(5, s.type());
        ps.setDate(6, s.dataDate() == null ? null : Date.valueOf(s.dataDate()));
        ps.setDouble(7, s.price());
        setNullableDouble(ps, 8, s.stopLoss());
        setNullableDouble(ps, 9, s.takeProfit());
        setNullableDouble(ps, 10, s.entryPrice());
        ps.setDate(11, s.entryDate() == null ? null : Date.valueOf(s.entryDate()));
        setNullableDouble(ps, 12, s.unrealizedPnlPct());
        ps.setInt(13, s.actionableTomorrow() ? 1 : 0);
        ps.setString(14, s.comment());
        setNullableDouble(ps, 15, s.prevClose());
        setNullableDouble(ps, 16, s.dayChangePct());
    }

    private static void setNullableDouble(PreparedStatement ps, int idx, Double value) throws SQLException {
        if (value == null) ps.setNull(idx, java.sql.Types.DECIMAL);
        else ps.setDouble(idx, value);
    }

    /** Stored signals for one market, ordered by sector then stock. */
    public List<StockSignal> findByMarket(String market) throws SQLException {
        List<StockSignal> out = new ArrayList<>();
        String sql = "SELECT " + COLS + " FROM " + T + " WHERE market = ? ORDER BY sector, stock";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, market);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(map(rs));
            }
        }
        return out;
    }

    private static StockSignal map(ResultSet rs) throws SQLException {
        Date dataDate = rs.getDate("data_date");
        Date entryDate = rs.getDate("entry_date");
        return new StockSignal(
                rs.getString("market"),
                dataDate == null ? null : dataDate.toLocalDate(),
                rs.getString("sector"),
                rs.getString("strategy"),
                rs.getString("stock"),
                rs.getString("signal_type"),
                rs.getDouble("price"),
                nullableDouble(rs, "stop_loss"),
                nullableDouble(rs, "take_profit"),
                nullableDouble(rs, "entry_price"),
                entryDate == null ? null : entryDate.toLocalDate(),
                nullableDouble(rs, "unrealized_pnl_pct"),
                rs.getString("comment"),
                rs.getInt("actionable_tomorrow") == 1,
                nullableDouble(rs, "prev_close"),
                nullableDouble(rs, "day_change_pct"));
    }

    private static Double nullableDouble(ResultSet rs, String col) throws SQLException {
        double v = rs.getDouble(col);
        return rs.wasNull() ? null : v;
    }

    /** Newest signal date for a market, or null when it has no rows. Used as the {@code asOf}. */
    public LocalDate latestDataDate(String market) throws SQLException {
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT MAX(data_date) FROM " + T + " WHERE market = ?")) {
            ps.setString(1, market);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Date d = rs.getDate(1);
                    return d == null ? null : d.toLocalDate();
                }
                return null;
            }
        }
    }
}
