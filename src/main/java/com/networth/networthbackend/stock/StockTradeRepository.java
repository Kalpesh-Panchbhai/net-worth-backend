package com.networth.networthbackend.stock;

import com.networth.networthbackend.db.Database;
import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Reads and writes {@code stock_trades} — backtest trade history. */
@Slf4j
public class StockTradeRepository {

    private static final String T = "stock_trades";
    private static final String COLS =
            "market, sector, strategy, stock, trade_num, entry_date, exit_date, entry_price, exit_price, "
            + "pnl, pnl_pct, mae_pct, mfe_pct, bars_held, comment";
    private static final String INSERT = "INSERT INTO " + T + " (" + COLS + ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
    private static final int BATCH = 1000;

    public List<StockTrade> findByStock(String market, String stock) throws SQLException {
        return query("WHERE market = ? AND stock = ? ORDER BY trade_num", market, stock);
    }

    public List<StockTrade> findBySector(String market, String sector) throws SQLException {
        return query("WHERE market = ? AND sector = ? ORDER BY stock, trade_num", market, sector);
    }

    /** All CLOSED trades for a market (excludes open "End of Data" rows), oldest exit first. */
    public List<StockTrade> findClosedByMarket(String market) throws SQLException {
        List<StockTrade> out = new ArrayList<>();
        String sql = "SELECT " + COLS + " FROM " + T + " WHERE market = ? AND exit_date IS NOT NULL "
                + "AND (comment IS NULL OR comment <> 'End of Data') ORDER BY exit_date";
        try (Connection c = Database.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, market);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) out.add(map(rs)); }
        }
        return out;
    }

    /** Most recent closed trades across a market (for the dashboard), newest exit first. */
    public List<StockTrade> recentClosed(String market, int limit) throws SQLException {
        List<StockTrade> out = new ArrayList<>();
        String sql = "SELECT " + COLS + " FROM " + T + " WHERE market = ? AND exit_date IS NOT NULL "
                + "ORDER BY exit_date DESC LIMIT ?";
        try (Connection c = Database.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, market);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) out.add(map(rs)); }
        }
        return out;
    }

    private List<StockTrade> query(String whereOrder, String p1, String p2) throws SQLException {
        List<StockTrade> out = new ArrayList<>();
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT " + COLS + " FROM " + T + " " + whereOrder)) {
            ps.setString(1, p1);
            ps.setString(2, p2);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) out.add(map(rs)); }
        }
        return out;
    }

    public int replaceForMarket(String market, List<StockTrade> trades) throws SQLException {
        try (Connection c = Database.getConnection()) {
            boolean auto = c.getAutoCommit();
            c.setAutoCommit(false);
            boolean ok = false;
            try (PreparedStatement del = c.prepareStatement("DELETE FROM " + T + " WHERE market = ?");
                 PreparedStatement ins = c.prepareStatement(INSERT)) {
                del.setString(1, market);
                del.executeUpdate();
                int n = 0, pending = 0;
                for (StockTrade t : trades) {
                    bind(ins, t);
                    ins.addBatch();
                    if (++pending >= BATCH) { n += ins.executeBatch().length; pending = 0; }
                }
                if (pending > 0) n += ins.executeBatch().length;
                c.commit();
                ok = true;
                return n;
            } finally {
                if (!ok) try { c.rollback(); } catch (SQLException ignore) {}
                try { c.setAutoCommit(auto); } catch (SQLException ignore) {}
            }
        }
    }

    private static void bind(PreparedStatement ps, StockTrade t) throws SQLException {
        ps.setString(1, t.market());
        ps.setString(2, t.sector());
        ps.setString(3, t.strategy());
        ps.setString(4, t.stock());
        ps.setInt(5, t.tradeNum());
        ps.setDate(6, t.entryDate() == null ? null : Date.valueOf(t.entryDate()));
        ps.setDate(7, t.exitDate() == null ? null : Date.valueOf(t.exitDate()));
        setD(ps, 8, t.entryPrice());
        setD(ps, 9, t.exitPrice());
        setD(ps, 10, t.pnl());
        setD(ps, 11, t.pnlPct());
        setD(ps, 12, t.maePct());
        setD(ps, 13, t.mfePct());
        if (t.barsHeld() == null) ps.setNull(14, java.sql.Types.INTEGER); else ps.setInt(14, t.barsHeld());
        ps.setString(15, t.comment());
    }

    private static void setD(PreparedStatement ps, int i, Double v) throws SQLException {
        if (v == null) ps.setNull(i, java.sql.Types.DECIMAL); else ps.setDouble(i, v);
    }

    private static StockTrade map(ResultSet rs) throws SQLException {
        Date ed = rs.getDate("entry_date");
        Date xd = rs.getDate("exit_date");
        int bh = rs.getInt("bars_held");
        Integer barsHeld = rs.wasNull() ? null : bh;
        return new StockTrade(
                rs.getString("market"), rs.getString("sector"), rs.getString("strategy"), rs.getString("stock"),
                rs.getInt("trade_num"), ed == null ? null : ed.toLocalDate(), xd == null ? null : xd.toLocalDate(),
                dN(rs, "entry_price"), dN(rs, "exit_price"), dN(rs, "pnl"), dN(rs, "pnl_pct"),
                dN(rs, "mae_pct"), dN(rs, "mfe_pct"), barsHeld, rs.getString("comment"));
    }

    private static Double dN(ResultSet rs, String col) throws SQLException {
        double v = rs.getDouble(col);
        return rs.wasNull() ? null : v;
    }
}
