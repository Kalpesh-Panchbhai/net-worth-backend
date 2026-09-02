package com.networth.networthbackend.fx;

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
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Reads and writes {@code fx_rates_daily}. */
@Slf4j
public class FxRateRepository {

    private static final String T = Constants.DB.FxRatesDaily.TABLE;
    private static final String C_BASE = Constants.DB.FxRatesDaily.COL_BASE_CURRENCY;
    private static final String C_QUOTE = Constants.DB.FxRatesDaily.COL_QUOTE_CURRENCY;
    private static final String C_DATE = Constants.DB.FxRatesDaily.COL_DATE;
    private static final String C_RATE = Constants.DB.FxRatesDaily.COL_RATE;

    private static final String SQL_UPSERT = "INSERT INTO " + T
            + " (" + C_BASE + ", " + C_QUOTE + ", " + C_DATE + ", " + C_RATE + ") VALUES (?,?,?,?)"
            + " ON DUPLICATE KEY UPDATE " + C_RATE + " = VALUES(" + C_RATE + ")";

    private static final String SQL_MAX_DATE = "SELECT MAX(" + C_DATE + ") FROM " + T;

    private static final String SQL_MIN_DATE = "SELECT MIN(" + C_DATE + ") FROM " + T;

    /**
     * Loads rate series converting each of {@code bases} into {@code quote} across [start, end].
     * <p>
     * One row set is fetched for all requested bases so a multi-currency page costs a single query.
     * The window is widened backwards by {@link #FLOOR_LOOKBACK_DAYS} days so a date landing on a
     * weekend or a market holiday still finds an earlier rate to floor onto.
     */
    private static final int FLOOR_LOOKBACK_DAYS = 10;

    public Map<String, TreeMap<LocalDate, BigDecimal>> loadSeries(Collection<String> bases, String quote,
                                                                  LocalDate start, LocalDate end) throws SQLException {
        Map<String, TreeMap<LocalDate, BigDecimal>> out = new HashMap<>();
        List<String> distinct = bases.stream().filter(b -> b != null && !b.equalsIgnoreCase(quote)).distinct().toList();
        if (distinct.isEmpty()) return out;

        String placeholders = String.join(", ", java.util.Collections.nCopies(distinct.size(), "?"));
        String sql = "SELECT " + C_BASE + ", " + C_DATE + ", " + C_RATE + " FROM " + T
                + " WHERE " + C_QUOTE + " = ? AND " + C_BASE + " IN (" + placeholders + ")"
                + " AND " + C_DATE + " BETWEEN ? AND ?";

        try (Connection conn = Database.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            int i = 1;
            ps.setString(i++, quote);
            for (String base : distinct) ps.setString(i++, base);
            ps.setDate(i++, Date.valueOf(start.minusDays(FLOOR_LOOKBACK_DAYS)));
            ps.setDate(i, Date.valueOf(end));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    // Upper-case the key: the column collation is case-insensitive, so SQL will
                    // match a row stored in any case, but the caller looks the map up by the
                    // normalised code.
                    String base = rs.getString(1).toUpperCase(java.util.Locale.ROOT);
                    out.computeIfAbsent(base, k -> new TreeMap<>())
                            .put(rs.getDate(2).toLocalDate(), rs.getBigDecimal(3));
                }
            }
        }
        return out;
    }

    /** Newest stored rate date, or {@code null} when the table is empty. */
    public LocalDate maxDate() throws SQLException {
        return singleDate(SQL_MAX_DATE);
    }

    /** Oldest stored rate date, or {@code null} when the table is empty. */
    public LocalDate minDate() throws SQLException {
        return singleDate(SQL_MIN_DATE);
    }

    private LocalDate singleDate(String sql) throws SQLException {
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                Date d = rs.getDate(1);
                return d == null ? null : d.toLocalDate();
            }
            return null;
        }
    }

    /** Batch-upserts rates. {@code rows} is (base, quote, date, rate). */
    public int upsertAll(List<Row> rows) throws SQLException {
        if (rows == null || rows.isEmpty()) return 0;
        int written = 0;
        try (Connection conn = Database.getConnection(); PreparedStatement ps = conn.prepareStatement(SQL_UPSERT)) {
            boolean autoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            boolean committed = false;
            try {
                int pending = 0;
                for (Row r : rows) {
                    ps.setString(1, r.base());
                    ps.setString(2, r.quote());
                    ps.setDate(3, Date.valueOf(r.date()));
                    ps.setBigDecimal(4, r.rate());
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
                // Catching Throwable, not just SQLException: an unchecked throwable escaping the
                // loop would otherwise reach setAutoCommit(true) below, which COMMITS the partial
                // batch. Roll back first, and never let a cleanup failure mask the real cause.
                if (!committed) {
                    try {
                        conn.rollback();
                    } catch (SQLException rollbackFailure) {
                        log.warn("FX rate rollback failed: {}", rollbackFailure.getMessage());
                    }
                }
                try {
                    conn.setAutoCommit(autoCommit);
                } catch (SQLException restoreFailure) {
                    log.warn("Restoring autoCommit failed: {}", restoreFailure.getMessage());
                }
            }
        }
        log.info("Upserted {} FX rate rows", written);
        return written;
    }

    private static final int BATCH_SIZE = 1_000;

    private static int count(int[] results) {
        int n = 0;
        for (int r : results) if (r >= 0 || r == PreparedStatement.SUCCESS_NO_INFO) n++;
        return n;
    }

    /** Earliest date any monetary history exists for, used to size the initial FX backfill. */
    public LocalDate earliestBusinessDate() throws SQLException {
        String sql = "SELECT LEAST("
                + "COALESCE((SELECT MIN(" + Constants.DB.Transactions.COL_TXN_DATE + ") FROM " + Constants.DB.Transactions.TABLE + "), CURRENT_DATE),"
                + "COALESCE((SELECT MIN(" + Constants.DB.AccountHoldingValuesDaily.COL_DATE + ") FROM " + Constants.DB.AccountHoldingValuesDaily.TABLE + "), CURRENT_DATE),"
                + "COALESCE((SELECT MIN(" + Constants.DB.Incomes.COL_CREDITED_DATE + ") FROM " + Constants.DB.Incomes.TABLE + "), CURRENT_DATE)"
                + ")";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                Date d = rs.getDate(1);
                if (d != null) return d.toLocalDate();
            }
            return null;
        }
    }

    public record Row(String base, String quote, LocalDate date, BigDecimal rate) {
    }

    /** Convenience for callers building rows for a whole pair. */
    public static List<Row> rowsFor(String base, String quote, Map<LocalDate, BigDecimal> rates) {
        List<Row> rows = new ArrayList<>(rates.size());
        for (Map.Entry<LocalDate, BigDecimal> e : rates.entrySet()) {
            rows.add(new Row(base, quote, e.getKey(), e.getValue()));
        }
        return rows;
    }
}
