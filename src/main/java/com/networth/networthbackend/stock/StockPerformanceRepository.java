package com.networth.networthbackend.stock;

import com.networth.networthbackend.db.Database;
import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Reads and writes {@code stock_performance} — per-stock backtest stats. */
@Slf4j
public class StockPerformanceRepository {

    private static final String T = "stock_performance";
    private static final String COLS =
            "market, sector, strategy, stock, cagr, mdd, wr, pf, trades, winners, losers, net_profit, "
            + "final_equity, sharpe, avg_bars_held, avg_trade_pct, avg_mae_pct, avg_mfe_pct, avg_hold_days";
    private static final String INSERT = "INSERT INTO " + T + " (" + COLS + ") "
            + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

    public List<StockPerformance> findByMarket(String market) throws SQLException {
        return query("WHERE market = ? ORDER BY sector, stock", market);
    }

    public List<StockPerformance> findBySector(String market, String sector) throws SQLException {
        List<StockPerformance> out = new ArrayList<>();
        String sql = "SELECT " + COLS + " FROM " + T + " WHERE market = ? AND sector = ? ORDER BY cagr DESC";
        try (Connection c = Database.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, market);
            ps.setString(2, sector);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) out.add(map(rs)); }
        }
        return out;
    }

    /** Per-stock stats keyed by ticker WITHOUT the Yahoo suffix (matches signal.stock). */
    public Map<String, StockPerformance> byBareStock(String market) throws SQLException {
        Map<String, StockPerformance> out = new HashMap<>();
        for (StockPerformance p : findByMarket(market)) {
            out.put(p.stock().toUpperCase().replace(".NS", ""), p);
        }
        return out;
    }

    private List<StockPerformance> query(String whereOrder, String market) throws SQLException {
        List<StockPerformance> out = new ArrayList<>();
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT " + COLS + " FROM " + T + " " + whereOrder)) {
            ps.setString(1, market);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) out.add(map(rs)); }
        }
        return out;
    }

    public int replaceForMarket(String market, List<StockPerformance> rows) throws SQLException {
        try (Connection c = Database.getConnection()) {
            boolean auto = c.getAutoCommit();
            c.setAutoCommit(false);
            boolean ok = false;
            try (PreparedStatement del = c.prepareStatement("DELETE FROM " + T + " WHERE market = ?");
                 PreparedStatement ins = c.prepareStatement(INSERT)) {
                del.setString(1, market);
                del.executeUpdate();
                for (StockPerformance p : rows) { bind(ins, p); ins.addBatch(); }
                int n = ins.executeBatch().length;
                c.commit();
                ok = true;
                return n;
            } finally {
                if (!ok) try { c.rollback(); } catch (SQLException ignore) {}
                try { c.setAutoCommit(auto); } catch (SQLException ignore) {}
            }
        }
    }

    private static void bind(PreparedStatement ps, StockPerformance p) throws SQLException {
        ps.setString(1, p.market());
        ps.setString(2, p.sector());
        ps.setString(3, p.strategy());
        ps.setString(4, p.stock());
        setD(ps, 5, p.cagr());
        setD(ps, 6, p.mdd());
        setD(ps, 7, p.wr());
        setD(ps, 8, p.pf());
        setI(ps, 9, p.trades());
        setI(ps, 10, p.winners());
        setI(ps, 11, p.losers());
        setD(ps, 12, p.netProfit());
        setD(ps, 13, p.finalEquity());
        setD(ps, 14, p.sharpe());
        setD(ps, 15, p.avgBarsHeld());
        setD(ps, 16, p.avgTradePct());
        setD(ps, 17, p.avgMaePct());
        setD(ps, 18, p.avgMfePct());
        setD(ps, 19, p.avgHoldDays());
    }

    private static void setD(PreparedStatement ps, int i, Double v) throws SQLException {
        if (v == null) ps.setNull(i, java.sql.Types.DECIMAL); else ps.setDouble(i, v);
    }

    private static void setI(PreparedStatement ps, int i, Integer v) throws SQLException {
        if (v == null) ps.setNull(i, java.sql.Types.INTEGER); else ps.setInt(i, v);
    }

    private static StockPerformance map(ResultSet rs) throws SQLException {
        return new StockPerformance(
                rs.getString("market"), rs.getString("sector"), rs.getString("strategy"), rs.getString("stock"),
                dN(rs, "cagr"), dN(rs, "mdd"), dN(rs, "wr"), dN(rs, "pf"),
                iN(rs, "trades"), iN(rs, "winners"), iN(rs, "losers"),
                dN(rs, "net_profit"), dN(rs, "final_equity"), dN(rs, "sharpe"), dN(rs, "avg_bars_held"),
                dN(rs, "avg_trade_pct"), dN(rs, "avg_mae_pct"), dN(rs, "avg_mfe_pct"), dN(rs, "avg_hold_days"));
    }

    private static Double dN(ResultSet rs, String col) throws SQLException {
        double v = rs.getDouble(col);
        return rs.wasNull() ? null : v;
    }

    private static Integer iN(ResultSet rs, String col) throws SQLException {
        int v = rs.getInt(col);
        return rs.wasNull() ? null : v;
    }
}
