package com.networth.networthbackend.mf;

import com.networth.networthbackend.common.Constants;
import com.networth.networthbackend.db.Database;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Reads and writes {@code mf_nav}. */
@Slf4j
public class MfNavRepository {

    private static final String T = Constants.DB.MfNav.TABLE;
    private static final String C_CODE = Constants.DB.MfNav.COL_SCHEME_CODE;
    private static final String C_DATE = Constants.DB.MfNav.COL_NAV_DATE;
    private static final String C_NAV = Constants.DB.MfNav.COL_NAV;

    /**
     * Large batches pay off here because the JDBC URL sets {@code rewriteBatchedStatements=true},
     * which collapses a batch into one multi-row INSERT. A full-history insert is ~3,400 rows.
     */
    private static final int BATCH_SIZE = 2_000;

    private static final String SQL_UPSERT = "INSERT INTO " + T
            + " (" + C_CODE + ", " + C_DATE + ", " + C_NAV + ") VALUES (?,?,?)"
            + " ON DUPLICATE KEY UPDATE " + C_NAV + " = VALUES(" + C_NAV + ")";

    /** One (scheme, date, nav) row. */
    public record Row(int schemeCode, LocalDate date, BigDecimal nav) {
    }

    /**
     * Batch-upserts NAV rows.
     * <p>
     * Idempotent by primary key, so re-running a backfill or a daily sync corrects revised NAVs
     * instead of duplicating or failing.
     */
    public int upsertAll(List<Row> rows) throws SQLException {
        if (rows == null || rows.isEmpty()) return 0;
        int written = 0;

        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_UPSERT)) {
            boolean autoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            boolean committed = false;
            try {
                int pending = 0;
                for (Row r : rows) {
                    ps.setInt(1, r.schemeCode());
                    ps.setDate(2, Date.valueOf(r.date()));
                    ps.setBigDecimal(3, r.nav());
                    ps.addBatch();
                    if (++pending >= BATCH_SIZE) {
                        written += count(ps.executeBatch());
                        pending = 0;
                    }
                }
                if (pending > 0) written += count(ps.executeBatch());
                conn.commit();
                committed = true;
            } finally {
                // setAutoCommit(true) commits an open transaction, so roll back first: otherwise an
                // unchecked throwable escaping the loop would persist a partial series.
                if (!committed) {
                    try {
                        conn.rollback();
                    } catch (SQLException rollbackFailure) {
                        log.warn("MF NAV rollback failed: {}", rollbackFailure.getMessage());
                    }
                }
                try {
                    conn.setAutoCommit(autoCommit);
                } catch (SQLException restoreFailure) {
                    log.warn("Restoring autoCommit failed: {}", restoreFailure.getMessage());
                }
            }
        }
        return written;
    }

    private static int count(int[] results) {
        int n = 0;
        for (int r : results) if (r >= 0 || r == PreparedStatement.SUCCESS_NO_INFO) n++;
        return n;
    }

    /** One scheme's series over [start, end], oldest first. Backs every metric computation. */
    public TreeMap<LocalDate, BigDecimal> loadSeries(int schemeCode, LocalDate start, LocalDate end)
            throws SQLException {
        String sql = "SELECT " + C_DATE + ", " + C_NAV + " FROM " + T
                + " WHERE " + C_CODE + " = ?"
                + (start == null ? "" : " AND " + C_DATE + " >= ?")
                + (end == null ? "" : " AND " + C_DATE + " <= ?")
                + " ORDER BY " + C_DATE;

        TreeMap<LocalDate, BigDecimal> series = new TreeMap<>();
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int i = 1;
            ps.setInt(i++, schemeCode);
            if (start != null) ps.setDate(i++, Date.valueOf(start));
            if (end != null) ps.setDate(i, Date.valueOf(end));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    series.put(rs.getDate(1).toLocalDate(), rs.getBigDecimal(2));
                }
            }
        }
        return series;
    }

    /**
     * Several schemes' series over [start, end] in one query, keyed by scheme code.
     * <p>
     * A category leaderboard needs every fund's NAV history at once; issuing one {@link #loadSeries}
     * per fund would be ~35 sequential round trips. A single {@code IN (...)} read streams them all
     * back over one connection, and the clustered {@code (scheme_code, nav_date)} key keeps each
     * fund's rows contiguous and already ordered.
     */
    public Map<Integer, TreeMap<LocalDate, BigDecimal>> loadSeriesForSchemes(
            List<Integer> schemeCodes, LocalDate start, LocalDate end) throws SQLException {
        Map<Integer, TreeMap<LocalDate, BigDecimal>> out = new LinkedHashMap<>();
        if (schemeCodes == null || schemeCodes.isEmpty()) return out;

        String placeholders = String.join(", ", java.util.Collections.nCopies(schemeCodes.size(), "?"));
        String sql = "SELECT " + C_CODE + ", " + C_DATE + ", " + C_NAV + " FROM " + T
                + " WHERE " + C_CODE + " IN (" + placeholders + ")"
                + (start == null ? "" : " AND " + C_DATE + " >= ?")
                + (end == null ? "" : " AND " + C_DATE + " <= ?")
                + " ORDER BY " + C_CODE + ", " + C_DATE;

        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int i = 1;
            for (Integer code : schemeCodes) ps.setInt(i++, code);
            if (start != null) ps.setDate(i++, Date.valueOf(start));
            if (end != null) ps.setDate(i, Date.valueOf(end));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.computeIfAbsent(rs.getInt(1), k -> new TreeMap<>())
                            .put(rs.getDate(2).toLocalDate(), rs.getBigDecimal(3));
                }
            }
        }
        return out;
    }

    /** Row count and date span per scheme. Used to validate a backfill before building on it. */
    public Map<Integer, Coverage> coverage() throws SQLException {
        String sql = "SELECT " + C_CODE + ", COUNT(*), MIN(" + C_DATE + "), MAX(" + C_DATE + ")"
                + " FROM " + T + " GROUP BY " + C_CODE;

        Map<Integer, Coverage> out = new LinkedHashMap<>();
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.put(rs.getInt(1), new Coverage(
                        rs.getInt(2),
                        rs.getDate(3).toLocalDate(),
                        rs.getDate(4).toLocalDate()));
            }
        }
        return out;
    }

    public record Coverage(int points, LocalDate from, LocalDate to) {
    }

    /** Row count and date span for one scheme, or null if it has no NAV. Backs the fund-detail read. */
    public Coverage coverageFor(int schemeCode) throws SQLException {
        String sql = "SELECT COUNT(*), MIN(" + C_DATE + "), MAX(" + C_DATE + ")"
                + " FROM " + T + " WHERE " + C_CODE + " = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, schemeCode);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                int points = rs.getInt(1);
                if (points == 0) return null;
                return new Coverage(points, rs.getDate(2).toLocalDate(), rs.getDate(3).toLocalDate());
            }
        }
    }

    /** Latest NAV value per scheme among those passed in, keyed by scheme code. */
    public Map<Integer, BigDecimal> latestNavFor(List<Integer> schemeCodes) throws SQLException {
        Map<Integer, BigDecimal> out = new LinkedHashMap<>();
        if (schemeCodes == null || schemeCodes.isEmpty()) return out;
        String placeholders = String.join(", ", java.util.Collections.nCopies(schemeCodes.size(), "?"));
        String sql = "SELECT n." + C_CODE + ", n." + C_NAV + " FROM " + T + " n"
                + " JOIN (SELECT " + C_CODE + ", MAX(" + C_DATE + ") md FROM " + T
                + " WHERE " + C_CODE + " IN (" + placeholders + ") GROUP BY " + C_CODE + ") t"
                + " ON t." + C_CODE + " = n." + C_CODE + " AND t.md = n." + C_DATE;
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int i = 1;
            for (Integer code : schemeCodes) ps.setInt(i++, code);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.put(rs.getInt(1), rs.getBigDecimal(2));
            }
        }
        return out;
    }

    /** Newest NAV date across all schemes, or null when {@code mf_nav} is empty. The metrics {@code as_of}. */
    public LocalDate latestNavDate() throws SQLException {
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT MAX(" + C_DATE + ") FROM " + T);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                Date d = rs.getDate(1);
                return d == null ? null : d.toLocalDate();
            }
            return null;
        }
    }

    /** Total stored NAV rows. */
    public long totalRows() throws SQLException {
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM " + T);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0L;
        }
    }

    /** Schemes holding no NAV row at all, among those passed in. */
    public List<Integer> findSchemesWithoutHistory(List<Integer> schemeCodes) throws SQLException {
        if (schemeCodes == null || schemeCodes.isEmpty()) return List.of();
        String placeholders = String.join(", ", java.util.Collections.nCopies(schemeCodes.size(), "?"));
        String sql = "SELECT DISTINCT " + C_CODE + " FROM " + T
                + " WHERE " + C_CODE + " IN (" + placeholders + ")";

        List<Integer> withHistory = new ArrayList<>();
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int i = 1;
            for (Integer code : schemeCodes) ps.setInt(i++, code);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) withHistory.add(rs.getInt(1));
            }
        }
        List<Integer> missing = new ArrayList<>(schemeCodes);
        missing.removeAll(withHistory);
        return missing;
    }
}
