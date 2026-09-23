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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Reads and writes {@code stock_positions} — the user's real portfolio (My Portfolio). */
@Slf4j
public class StockPositionRepository {

    private static final String T = "stock_positions";
    private static final String COLS =
            "id, market, stock, sector, strategy, entry_date, entry_price, quantity, notes, status, exit_price, exit_date";

    private static final String UPSERT = "INSERT INTO " + T + " (" + COLS + ") "
            + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?) "
            + "ON DUPLICATE KEY UPDATE market=VALUES(market), stock=VALUES(stock), sector=VALUES(sector), "
            + "strategy=VALUES(strategy), entry_date=VALUES(entry_date), entry_price=VALUES(entry_price), "
            + "quantity=VALUES(quantity), notes=VALUES(notes), status=VALUES(status), exit_price=VALUES(exit_price), "
            + "exit_date=VALUES(exit_date)";

    public List<StockPosition> findByMarket(String market) throws SQLException {
        List<StockPosition> out = new ArrayList<>();
        String sql = "SELECT " + COLS + " FROM " + T + " WHERE market = ? ORDER BY status, entry_date DESC";
        try (Connection c = Database.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, market);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(map(rs));
            }
        }
        return out;
    }

    /** Open positions' tickers for a market, suffix-stripped + uppercased to match signal stocks. */
    public Set<String> heldStocks(String market) throws SQLException {
        Set<String> out = new LinkedHashSet<>();
        String sql = "SELECT stock FROM " + T + " WHERE market = ? AND status = 'open'";
        try (Connection c = Database.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, market);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(rs.getString(1).toUpperCase().replace(".NS", ""));
            }
        }
        return out;
    }

    public void upsert(StockPosition p) throws SQLException {
        try (Connection c = Database.getConnection(); PreparedStatement ps = c.prepareStatement(UPSERT)) {
            bind(ps, p);
            ps.executeUpdate();
        }
    }

    /** Bulk replace for one market (used by the VM importer). */
    public int replaceForMarket(String market, List<StockPosition> positions) throws SQLException {
        try (Connection c = Database.getConnection()) {
            boolean auto = c.getAutoCommit();
            c.setAutoCommit(false);
            boolean ok = false;
            try (PreparedStatement del = c.prepareStatement("DELETE FROM " + T + " WHERE market = ?");
                 PreparedStatement ins = c.prepareStatement(UPSERT)) {
                del.setString(1, market);
                del.executeUpdate();
                for (StockPosition p : positions) { bind(ins, p); ins.addBatch(); }
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

    public void deleteById(String id) throws SQLException {
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM " + T + " WHERE id = ?")) {
            ps.setString(1, id);
            ps.executeUpdate();
        }
    }

    private static void bind(PreparedStatement ps, StockPosition p) throws SQLException {
        ps.setString(1, p.id());
        ps.setString(2, p.market());
        ps.setString(3, p.stock());
        ps.setString(4, p.sector());
        ps.setString(5, p.strategy());
        ps.setDate(6, p.entryDate() == null ? null : Date.valueOf(p.entryDate()));
        setD(ps, 7, p.entryPrice());
        setD(ps, 8, p.quantity());
        ps.setString(9, p.notes());
        ps.setString(10, p.status() == null ? "open" : p.status());
        setD(ps, 11, p.exitPrice());
        ps.setDate(12, p.exitDate() == null ? null : Date.valueOf(p.exitDate()));
    }

    private static void setD(PreparedStatement ps, int i, Double v) throws SQLException {
        if (v == null) ps.setNull(i, java.sql.Types.DECIMAL); else ps.setDouble(i, v);
    }

    private static StockPosition map(ResultSet rs) throws SQLException {
        Date ed = rs.getDate("entry_date");
        Date xd = rs.getDate("exit_date");
        return new StockPosition(
                rs.getString("id"), rs.getString("market"), rs.getString("stock"),
                rs.getString("sector"), rs.getString("strategy"),
                ed == null ? null : ed.toLocalDate(), dN(rs, "entry_price"), dN(rs, "quantity"),
                rs.getString("notes"), rs.getString("status"), dN(rs, "exit_price"),
                xd == null ? null : xd.toLocalDate());
    }

    private static Double dN(ResultSet rs, String col) throws SQLException {
        double v = rs.getDouble(col);
        return rs.wasNull() ? null : v;
    }
}
