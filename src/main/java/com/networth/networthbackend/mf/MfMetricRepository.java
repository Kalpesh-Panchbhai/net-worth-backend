package com.networth.networthbackend.mf;

import com.networth.networthbackend.common.Constants;
import com.networth.networthbackend.db.Database;
import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Reads and writes {@code mf_metric} and {@code mf_rank}. */
@Slf4j
public class MfMetricRepository {

    private static final String MT = Constants.DB.MfMetric.TABLE;
    private static final String M_CODE = Constants.DB.MfMetric.COL_SCHEME_CODE;
    private static final String M_AS_OF = Constants.DB.MfMetric.COL_AS_OF;
    private static final String M_HORIZON = Constants.DB.MfMetric.COL_HORIZON;
    private static final String M_METRIC = Constants.DB.MfMetric.COL_METRIC;
    private static final String M_VALUE = Constants.DB.MfMetric.COL_VALUE;

    private static final String RT = Constants.DB.MfRank.TABLE;
    private static final String R_SUB = Constants.DB.MfRank.COL_SUB_CATEGORY;
    private static final String R_AS_OF = Constants.DB.MfRank.COL_AS_OF;
    private static final String R_HORIZON = Constants.DB.MfRank.COL_HORIZON;
    private static final String R_METRIC = Constants.DB.MfRank.COL_METRIC;
    private static final String R_CODE = Constants.DB.MfRank.COL_SCHEME_CODE;
    private static final String R_RANK = Constants.DB.MfRank.COL_RANK;
    private static final String R_PERCENTILE = Constants.DB.MfRank.COL_PERCENTILE;
    private static final String R_PEERS = Constants.DB.MfRank.COL_PEER_COUNT;

    private static final int BATCH_SIZE = 2_000;

    private static final String SQL_UPSERT_METRIC = "INSERT INTO " + MT
            + " (" + M_CODE + ", " + M_AS_OF + ", " + M_HORIZON + ", " + M_METRIC + ", " + M_VALUE + ")"
            + " VALUES (?,?,?,?,?)"
            + " ON DUPLICATE KEY UPDATE " + M_VALUE + " = VALUES(" + M_VALUE + ")";

    private static final String SQL_UPSERT_RANK = "INSERT INTO " + RT
            + " (" + R_SUB + ", " + R_AS_OF + ", " + R_HORIZON + ", " + R_METRIC + ", " + R_CODE + ", "
            + R_RANK + ", " + R_PERCENTILE + ", " + R_PEERS + ") VALUES (?,?,?,?,?,?,?,?)"
            + " ON DUPLICATE KEY UPDATE "
            + R_RANK + " = VALUES(" + R_RANK + "), "
            + R_PERCENTILE + " = VALUES(" + R_PERCENTILE + "), "
            + R_PEERS + " = VALUES(" + R_PEERS + ")";

    /** One metric value for one fund. */
    public record MetricRow(int schemeCode, LocalDate asOf, MfMetricKey.Horizon horizon,
                            MfMetricKey.Metric metric, double value) {
    }

    /** One fund's standing in its peer group on one (horizon, metric). */
    public record RankRow(String subCategory, LocalDate asOf, MfMetricKey.Horizon horizon,
                          MfMetricKey.Metric metric, int schemeCode, int rank,
                          double percentile, int peerCount) {
    }

    public int upsertMetrics(List<MetricRow> rows) throws SQLException {
        return batchUpsert(rows, SQL_UPSERT_METRIC, (ps, r) -> {
            ps.setInt(1, r.schemeCode());
            ps.setDate(2, Date.valueOf(r.asOf()));
            ps.setString(3, r.horizon().code);
            ps.setString(4, r.metric().code);
            ps.setDouble(5, r.value());
        }, "mf_metric");
    }

    public int upsertRanks(List<RankRow> rows) throws SQLException {
        return batchUpsert(rows, SQL_UPSERT_RANK, (ps, r) -> {
            ps.setString(1, r.subCategory());
            ps.setDate(2, Date.valueOf(r.asOf()));
            ps.setString(3, r.horizon().code);
            ps.setString(4, r.metric().code);
            ps.setInt(5, r.schemeCode());
            ps.setInt(6, r.rank());
            ps.setDouble(7, r.percentile());
            ps.setInt(8, r.peerCount());
        }, "mf_rank");
    }

    /** Binds one row onto a statement. Declared here so the batch plumbing is written once. */
    private interface Binder<T> {
        void bind(PreparedStatement ps, T row) throws SQLException;
    }

    private <T> int batchUpsert(List<T> rows, String sql, Binder<T> binder, String label)
            throws SQLException {
        if (rows == null || rows.isEmpty()) return 0;
        int written = 0;

        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            boolean autoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            boolean committed = false;
            try {
                int pending = 0;
                for (T row : rows) {
                    binder.bind(ps, row);
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
                // setAutoCommit(true) would commit an open transaction, so roll back explicitly
                // before restoring it — otherwise a throwable escaping the loop persists a partial
                // recompute, which is worse than none.
                if (!committed) {
                    try {
                        conn.rollback();
                    } catch (SQLException rollbackFailure) {
                        log.warn("{} rollback failed: {}", label, rollbackFailure.getMessage());
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

    /** Every metric for one fund at the newest as_of. Backs the fund-detail page. */
    public Map<MfMetricKey.Horizon, Map<MfMetricKey.Metric, Double>> loadForScheme(int schemeCode)
            throws SQLException {
        String sql = "SELECT " + M_HORIZON + ", " + M_METRIC + ", " + M_VALUE + " FROM " + MT
                + " WHERE " + M_CODE + " = ?"
                + " AND " + M_AS_OF + " = (SELECT MAX(" + M_AS_OF + ") FROM " + MT
                + " WHERE " + M_CODE + " = ?)";

        Map<MfMetricKey.Horizon, Map<MfMetricKey.Metric, Double>> out =
                new EnumMap<>(MfMetricKey.Horizon.class);
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, schemeCode);
            ps.setInt(2, schemeCode);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.computeIfAbsent(MfMetricKey.horizon(rs.getString(1)),
                                    k -> new EnumMap<>(MfMetricKey.Metric.class))
                            .put(MfMetricKey.metric(rs.getString(2)), rs.getDouble(3));
                }
            }
        }
        return out;
    }

    /**
     * Top {@code limit} funds in a category on one (horizon, metric), best first.
     * <p>
     * This is the "best fund per category" read, and the reason the metrics tables exist: it costs
     * one indexed scan of at most {@code limit} rows instead of loading every peer's NAV series.
     */
    public List<RankRow> topInCategory(String subCategory, MfMetricKey.Horizon horizon,
                                       MfMetricKey.Metric metric, int limit) throws SQLException {
        String sql = "SELECT " + R_SUB + ", " + R_AS_OF + ", " + R_HORIZON + ", " + R_METRIC + ", "
                + R_CODE + ", " + R_RANK + ", " + R_PERCENTILE + ", " + R_PEERS + " FROM " + RT
                + " WHERE " + R_SUB + " = ? AND " + R_HORIZON + " = ? AND " + R_METRIC + " = ?"
                + " AND " + R_AS_OF + " = (SELECT MAX(" + R_AS_OF + ") FROM " + RT + ")"
                + " ORDER BY " + R_RANK + " LIMIT ?";

        List<RankRow> out = new ArrayList<>();
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, subCategory);
            ps.setString(2, horizon.code);
            ps.setString(3, metric.code);
            ps.setInt(4, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(readRank(rs));
            }
        }
        return out;
    }

    /** A leaderboard row assembled server-side: the rank, its metric value, and the fund's identity. */
    public record LeaderboardRow(int rank, int schemeCode, String name, String amc, double value,
                                 LocalDate launchDate, LocalDate lastNavDate, int peerCount) {
    }

    /**
     * The top {@code limit} funds of a category on one (horizon, metric), best first, with the value
     * and scheme identity each carries — everything the leaderboard renders, in one indexed read.
     * <p>
     * Joins the rank to its metric value (same {@code as_of}) and to the scheme master, so the
     * caller does not fan out into per-fund lookups. This is the read the whole precompute exists to
     * make cheap: ≤{@code limit} rows instead of every peer's NAV series.
     */
    public List<LeaderboardRow> topInCategoryDetailed(String subCategory, MfMetricKey.Horizon horizon,
                                                      MfMetricKey.Metric metric, int limit) throws SQLException {
        String s = Constants.DB.MfSchemes.TABLE;
        String sql = "SELECT r." + R_RANK + ", r." + R_CODE + ", s." + Constants.DB.MfSchemes.COL_NAME
                + ", s." + Constants.DB.MfSchemes.COL_AMC + ", m." + M_VALUE
                + ", s." + Constants.DB.MfSchemes.COL_LAUNCH_DATE + ", s." + Constants.DB.MfSchemes.COL_LAST_NAV_DATE
                + ", r." + R_PEERS
                + " FROM " + RT + " r"
                + " JOIN " + s + " s ON s." + Constants.DB.MfSchemes.COL_SCHEME_CODE + " = r." + R_CODE
                + " JOIN " + MT + " m ON m." + M_CODE + " = r." + R_CODE
                + " AND m." + M_AS_OF + " = r." + R_AS_OF
                + " AND m." + M_HORIZON + " = r." + R_HORIZON
                + " AND m." + M_METRIC + " = r." + R_METRIC
                + " WHERE r." + R_SUB + " = ? AND r." + R_HORIZON + " = ? AND r." + R_METRIC + " = ?"
                + " AND r." + R_AS_OF + " = (SELECT MAX(" + R_AS_OF + ") FROM " + RT + ")"
                + " ORDER BY r." + R_RANK + " LIMIT ?";

        List<LeaderboardRow> out = new ArrayList<>();
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, subCategory);
            ps.setString(2, horizon.code);
            ps.setString(3, metric.code);
            ps.setInt(4, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Date launch = rs.getDate(6);
                    Date lastNav = rs.getDate(7);
                    out.add(new LeaderboardRow(rs.getInt(1), rs.getInt(2), rs.getString(3), rs.getString(4),
                            rs.getDouble(5),
                            launch == null ? null : launch.toLocalDate(),
                            lastNav == null ? null : lastNav.toLocalDate(),
                            rs.getInt(8)));
                }
            }
        }
        return out;
    }

    /** One (fund, metric, value) at a horizon, before the service groups them into per-fund rows. */
    public record TableMetricRow(int schemeCode, String name, String amc, String assetClass,
                                 String subCategory, String metric, double value) {
    }

    /**
     * Every requested metric at one horizon for a set of funds, for the sortable browse table. Pass
     * {@code subCategory == null} to span all live Direct+Growth funds; pass one to scope to a
     * category. One indexed read of the newest as_of — the whole grid a table renders.
     */
    public List<TableMetricRow> tableRows(String subCategory, MfMetricKey.Horizon horizon,
                                          List<MfMetricKey.Metric> metrics) throws SQLException {
        if (metrics == null || metrics.isEmpty()) return new ArrayList<>();
        String s = Constants.DB.MfSchemes.TABLE;
        String metricPlaceholders = String.join(", ", java.util.Collections.nCopies(metrics.size(), "?"));
        String sql = "SELECT s." + Constants.DB.MfSchemes.COL_SCHEME_CODE + ", s." + Constants.DB.MfSchemes.COL_NAME
                + ", s." + Constants.DB.MfSchemes.COL_AMC + ", s." + Constants.DB.MfSchemes.COL_ASSET_CLASS
                + ", s." + Constants.DB.MfSchemes.COL_SUB_CATEGORY + ", m." + M_METRIC + ", m." + M_VALUE
                + " FROM " + MT + " m"
                + " JOIN " + s + " s ON s." + Constants.DB.MfSchemes.COL_SCHEME_CODE + " = m." + M_CODE
                + " WHERE m." + M_AS_OF + " = (SELECT MAX(" + M_AS_OF + ") FROM " + MT + ")"
                + " AND m." + M_HORIZON + " = ?"
                + " AND m." + M_METRIC + " IN (" + metricPlaceholders + ")"
                + " AND s." + Constants.DB.MfSchemes.COL_IS_LIVE + " = 1"
                + (subCategory == null ? "" : " AND s." + Constants.DB.MfSchemes.COL_SUB_CATEGORY + " = ?");

        List<TableMetricRow> out = new ArrayList<>();
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int i = 1;
            ps.setString(i++, horizon.code);
            for (MfMetricKey.Metric m : metrics) ps.setString(i++, m.code);
            if (subCategory != null) ps.setString(i, subCategory);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new TableMetricRow(rs.getInt(1), rs.getString(2), rs.getString(3),
                            rs.getString(4), rs.getString(5), rs.getString(6), rs.getDouble(7)));
                }
            }
        }
        return out;
    }

    /** One fund's standing on a (horizon, metric): where it sits in its category. */
    public record SchemeRank(MfMetricKey.Horizon horizon, MfMetricKey.Metric metric, int rank,
                             double percentile, int peerCount) {
    }

    /** Every (horizon, metric) ranking for one fund at the newest as_of. Backs the fund-detail badges. */
    public List<SchemeRank> ranksForScheme(int schemeCode) throws SQLException {
        String sql = "SELECT " + R_HORIZON + ", " + R_METRIC + ", " + R_RANK + ", " + R_PERCENTILE + ", " + R_PEERS
                + " FROM " + RT + " WHERE " + R_CODE + " = ?"
                + " AND " + R_AS_OF + " = (SELECT MAX(" + R_AS_OF + ") FROM " + RT + ")";
        List<SchemeRank> out = new ArrayList<>();
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, schemeCode);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new SchemeRank(MfMetricKey.horizon(rs.getString(1)), MfMetricKey.metric(rs.getString(2)),
                            rs.getInt(3), rs.getDouble(4), rs.getInt(5)));
                }
            }
        }
        return out;
    }

    /** How many funds were rankable on one (horizon, metric) at the newest as_of — the "of N" numerator. */
    public int countRanked(String subCategory, MfMetricKey.Horizon horizon, MfMetricKey.Metric metric)
            throws SQLException {
        String sql = "SELECT COUNT(*) FROM " + RT
                + " WHERE " + R_SUB + " = ? AND " + R_HORIZON + " = ? AND " + R_METRIC + " = ?"
                + " AND " + R_AS_OF + " = (SELECT MAX(" + R_AS_OF + ") FROM " + RT + ")";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, subCategory);
            ps.setString(2, horizon.code);
            ps.setString(3, metric.code);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private static RankRow readRank(ResultSet rs) throws SQLException {
        return new RankRow(
                rs.getString(1),
                rs.getDate(2).toLocalDate(),
                MfMetricKey.horizon(rs.getString(3)),
                MfMetricKey.metric(rs.getString(4)),
                rs.getInt(5),
                rs.getInt(6),
                rs.getDouble(7),
                rs.getInt(8));
    }

    /** Newest as_of present in {@code mf_metric}, or null when empty. */
    public LocalDate latestAsOf() throws SQLException {
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT MAX(" + M_AS_OF + ") FROM " + MT);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                Date d = rs.getDate(1);
                return d == null ? null : d.toLocalDate();
            }
            return null;
        }
    }

    /**
     * Deletes metric and rank rows older than {@code keepFrom}, except month-end snapshots.
     * <p>
     * ~70k metric rows per as_of would add ~25M rows a year if every day were kept. Month-ends are
     * retained so a later "how has this fund's percentile drifted" feature stays possible.
     */
    public int prune(LocalDate keepFrom) throws SQLException {
        int deleted = 0;
        for (String table : new String[]{MT, RT}) {
            String asOf = table.equals(MT) ? M_AS_OF : R_AS_OF;
            String sql = "DELETE FROM " + table + " WHERE " + asOf + " < ?"
                    + " AND " + asOf + " <> LAST_DAY(" + asOf + ")";
            try (Connection conn = Database.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setDate(1, Date.valueOf(keepFrom));
                deleted += ps.executeUpdate();
            }
        }
        if (deleted > 0) log.info("Pruned {} stale metric/rank rows before {}", deleted, keepFrom);
        return deleted;
    }
}
