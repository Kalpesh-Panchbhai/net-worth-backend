package com.networth.networthbackend.mf;

import com.networth.networthbackend.common.Constants;
import com.networth.networthbackend.db.Database;
import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Reads and writes {@code mf_schemes} and {@code mf_category_unmapped}. */
@Slf4j
public class MfSchemeRepository {

    private static final String T = Constants.DB.MfSchemes.TABLE;
    private static final String C_CODE = Constants.DB.MfSchemes.COL_SCHEME_CODE;
    private static final String C_ISIN = Constants.DB.MfSchemes.COL_ISIN;
    private static final String C_AMC = Constants.DB.MfSchemes.COL_AMC;
    private static final String C_NAME = Constants.DB.MfSchemes.COL_NAME;
    private static final String C_NAV_NAME = Constants.DB.MfSchemes.COL_NAV_NAME;
    private static final String C_RAW_CATEGORY = Constants.DB.MfSchemes.COL_RAW_CATEGORY;
    private static final String C_ASSET_CLASS = Constants.DB.MfSchemes.COL_ASSET_CLASS;
    private static final String C_SUB_CATEGORY = Constants.DB.MfSchemes.COL_SUB_CATEGORY;
    private static final String C_PLAN = Constants.DB.MfSchemes.COL_PLAN;
    private static final String C_OPTION = Constants.DB.MfSchemes.COL_OPTION;
    private static final String C_LAUNCH_DATE = Constants.DB.MfSchemes.COL_LAUNCH_DATE;
    private static final String C_IS_LIVE = Constants.DB.MfSchemes.COL_IS_LIVE;
    private static final String C_LAST_NAV_DATE = Constants.DB.MfSchemes.COL_LAST_NAV_DATE;

    private static final int BATCH_SIZE = 500;

    private static final String SQL_UPSERT = "INSERT INTO " + T + " ("
            + C_CODE + ", " + C_ISIN + ", " + C_AMC + ", " + C_NAME + ", " + C_NAV_NAME + ", "
            + C_RAW_CATEGORY + ", " + C_ASSET_CLASS + ", " + C_SUB_CATEGORY + ", "
            + C_PLAN + ", " + C_OPTION + ", " + C_LAUNCH_DATE + ", " + C_IS_LIVE + ", " + C_LAST_NAV_DATE
            + ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)"
            + " ON DUPLICATE KEY UPDATE "
            + C_ISIN + " = VALUES(" + C_ISIN + "), "
            + C_AMC + " = VALUES(" + C_AMC + "), "
            + C_NAME + " = VALUES(" + C_NAME + "), "
            + C_NAV_NAME + " = VALUES(" + C_NAV_NAME + "), "
            + C_RAW_CATEGORY + " = VALUES(" + C_RAW_CATEGORY + "), "
            + C_ASSET_CLASS + " = VALUES(" + C_ASSET_CLASS + "), "
            + C_SUB_CATEGORY + " = VALUES(" + C_SUB_CATEGORY + "), "
            + C_PLAN + " = VALUES(" + C_PLAN + "), "
            + C_OPTION + " = VALUES(" + C_OPTION + "), "
            + C_LAUNCH_DATE + " = VALUES(" + C_LAUNCH_DATE + "), "
            + C_IS_LIVE + " = VALUES(" + C_IS_LIVE + "), "
            + C_LAST_NAV_DATE + " = VALUES(" + C_LAST_NAV_DATE + ")";

    /**
     * Batch-upserts the scheme master.
     * <p>
     * A scheme that has dropped out of the feed is not deleted — NAV history for it stays valid and
     * a fund can vanish for a day over an AMFI publishing glitch. The sync clears {@code is_live}
     * instead, and rankings filter on that.
     */
    public int upsertAll(List<MfScheme> schemes) throws SQLException {
        if (schemes == null || schemes.isEmpty()) return 0;
        int written = 0;

        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_UPSERT)) {
            boolean autoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            boolean committed = false;
            try {
                int pending = 0;
                for (MfScheme s : schemes) {
                    bind(ps, s);
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
                // Mirrors FxRateRepository: setAutoCommit(true) commits an open transaction, so an
                // unchecked throwable escaping the loop would otherwise persist a partial batch.
                if (!committed) {
                    try {
                        conn.rollback();
                    } catch (SQLException rollbackFailure) {
                        log.warn("MF scheme rollback failed: {}", rollbackFailure.getMessage());
                    }
                }
                try {
                    conn.setAutoCommit(autoCommit);
                } catch (SQLException restoreFailure) {
                    log.warn("Restoring autoCommit failed: {}", restoreFailure.getMessage());
                }
            }
        }
        log.info("Upserted {} mf_schemes rows", written);
        return written;
    }

    private static void bind(PreparedStatement ps, MfScheme s) throws SQLException {
        ps.setInt(1, s.schemeCode());
        setNullableString(ps, 2, s.isin());
        ps.setString(3, s.amc());
        ps.setString(4, s.name());
        ps.setString(5, s.navName());
        ps.setString(6, s.rawCategory());
        ps.setString(7, s.assetClass().name());
        ps.setString(8, s.subCategory());
        ps.setString(9, s.plan().name());
        ps.setString(10, s.option().name());
        setNullableDate(ps, 11, s.launchDate());
        ps.setBoolean(12, s.isLive());
        setNullableDate(ps, 13, s.lastNavDate());
    }

    private static void setNullableString(PreparedStatement ps, int index, String value) throws SQLException {
        if (value == null) ps.setNull(index, Types.VARCHAR);
        else ps.setString(index, value);
    }

    private static void setNullableDate(PreparedStatement ps, int index, LocalDate value) throws SQLException {
        if (value == null) ps.setNull(index, Types.DATE);
        else ps.setDate(index, Date.valueOf(value));
    }

    private static int count(int[] results) {
        int n = 0;
        for (int r : results) if (r >= 0 || r == PreparedStatement.SUCCESS_NO_INFO) n++;
        return n;
    }

    /**
     * Clears {@code is_live} for stored schemes absent from the current snapshot.
     * <p>
     * Done as one statement keyed on the codes that ARE live, so a scheme whose category or plan
     * classification changed still gets flagged correctly.
     */
    public int markMissingAsNotLive(List<Integer> liveSchemeCodes) throws SQLException {
        if (liveSchemeCodes == null || liveSchemeCodes.isEmpty()) {
            // Refuse to blank the whole table: an empty snapshot means the fetch failed, not that
            // every fund in India was wound up.
            log.warn("Empty live scheme list; leaving is_live untouched");
            return 0;
        }
        String placeholders = String.join(", ", java.util.Collections.nCopies(liveSchemeCodes.size(), "?"));
        String sql = "UPDATE " + T + " SET " + C_IS_LIVE + " = 0"
                + " WHERE " + C_IS_LIVE + " = 1 AND " + C_CODE + " NOT IN (" + placeholders + ")";

        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int i = 1;
            for (Integer code : liveSchemeCodes) ps.setInt(i++, code);
            int cleared = ps.executeUpdate();
            if (cleared > 0) log.info("Marked {} schemes as no longer live", cleared);
            return cleared;
        }
    }

    /** Records category strings the resolver did not recognise, with how many schemes each hit. */
    public void recordUnmappedCategories(Map<String, Integer> countsByRawCategory) throws SQLException {
        if (countsByRawCategory == null || countsByRawCategory.isEmpty()) return;

        String sql = "INSERT INTO " + Constants.DB.MfCategoryUnmapped.TABLE + " ("
                + Constants.DB.MfCategoryUnmapped.COL_RAW_CATEGORY + ", "
                + Constants.DB.MfCategoryUnmapped.COL_SCHEME_COUNT + ") VALUES (?,?)"
                + " ON DUPLICATE KEY UPDATE "
                + Constants.DB.MfCategoryUnmapped.COL_SCHEME_COUNT + " = VALUES("
                + Constants.DB.MfCategoryUnmapped.COL_SCHEME_COUNT + ")";

        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Map.Entry<String, Integer> e : countsByRawCategory.entrySet()) {
                ps.setString(1, e.getKey());
                ps.setInt(2, e.getValue());
                ps.addBatch();
            }
            ps.executeBatch();
        }
        log.warn("Unmapped AMFI categories recorded: {}", countsByRawCategory.keySet());
    }

    /** Scheme codes eligible for NAV ingest and ranking: live Direct+Growth share classes. */
    public List<Integer> findRankableSchemeCodes() throws SQLException {
        String sql = "SELECT " + C_CODE + " FROM " + T
                + " WHERE " + C_IS_LIVE + " = 1"
                + " AND " + C_PLAN + " = ?"
                + " AND " + C_OPTION + " = ?"
                + " AND " + C_ASSET_CLASS + " <> ?"
                + " ORDER BY " + C_CODE;

        List<Integer> codes = new ArrayList<>();
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, MfPlanOption.Plan.DIRECT.name());
            ps.setString(2, MfPlanOption.Option.GROWTH.name());
            ps.setString(3, AssetClass.UNKNOWN.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) codes.add(rs.getInt(1));
            }
        }
        return codes;
    }

    /**
     * Rankable schemes whose full history has not been pulled yet.
     * <p>
     * The backfill is ~1,760 outbound calls against a free third-party API, so it has to be
     * resumable: an interrupted run continues from here rather than starting over.
     */
    public List<Integer> findSchemesNeedingBackfill() throws SQLException {
        String sql = "SELECT " + C_CODE + " FROM " + T
                + " WHERE " + C_IS_LIVE + " = 1"
                + " AND " + C_PLAN + " = ?"
                + " AND " + C_OPTION + " = ?"
                + " AND " + Constants.DB.MfSchemes.COL_NAV_BACKFILLED_AT + " IS NULL"
                + " ORDER BY " + C_CODE;

        List<Integer> codes = new ArrayList<>();
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, MfPlanOption.Plan.DIRECT.name());
            ps.setString(2, MfPlanOption.Option.GROWTH.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) codes.add(rs.getInt(1));
            }
        }
        return codes;
    }

    /** Stamps schemes whose history has been stored, so a resumed backfill skips them. */
    public int markBackfilled(List<Integer> schemeCodes) throws SQLException {
        if (schemeCodes == null || schemeCodes.isEmpty()) return 0;
        String sql = "UPDATE " + T + " SET " + Constants.DB.MfSchemes.COL_NAV_BACKFILLED_AT
                + " = CURRENT_TIMESTAMP WHERE " + C_CODE + " = ?";

        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Integer code : schemeCodes) {
                ps.setInt(1, code);
                ps.addBatch();
            }
            return count(ps.executeBatch());
        }
    }

    /** Columns in {@link MfScheme} order, for the read mappers below. */
    private static final String READ_COLS = String.join(", ",
            C_CODE, C_ISIN, C_AMC, C_NAME, C_NAV_NAME, C_RAW_CATEGORY, C_ASSET_CLASS,
            C_SUB_CATEGORY, C_PLAN, C_OPTION, C_LAUNCH_DATE, C_IS_LIVE, C_LAST_NAV_DATE);

    /** Live Direct+Growth schemes in one sub-category, oldest launch first. Backs a category leaderboard. */
    public List<MfScheme> findLiveBySubCategory(String subCategory) throws SQLException {
        String sql = "SELECT " + READ_COLS + " FROM " + T
                + " WHERE " + C_IS_LIVE + " = 1"
                + " AND " + C_PLAN + " = ?"
                + " AND " + C_OPTION + " = ?"
                + " AND " + C_SUB_CATEGORY + " = ?"
                + " ORDER BY " + C_LAUNCH_DATE;

        List<MfScheme> out = new ArrayList<>();
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, MfPlanOption.Plan.DIRECT.name());
            ps.setString(2, MfPlanOption.Option.GROWTH.name());
            ps.setString(3, subCategory);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(map(rs));
            }
        }
        return out;
    }

    /** One scheme by AMFI code, regardless of plan/option/live status. Backs the fund-detail read. */
    public Optional<MfScheme> findByCode(int schemeCode) throws SQLException {
        String sql = "SELECT " + READ_COLS + " FROM " + T + " WHERE " + C_CODE + " = ?";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, schemeCode);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    private static MfScheme map(ResultSet rs) throws SQLException {
        Date launch = rs.getDate(11);
        Date lastNav = rs.getDate(13);
        return MfScheme.builder()
                .schemeCode(rs.getInt(1))
                .isin(rs.getString(2))
                .amc(rs.getString(3))
                .name(rs.getString(4))
                .navName(rs.getString(5))
                .rawCategory(rs.getString(6))
                .assetClass(AssetClass.valueOf(rs.getString(7)))
                .subCategory(rs.getString(8))
                .plan(MfPlanOption.Plan.valueOf(rs.getString(9)))
                .option(MfPlanOption.Option.valueOf(rs.getString(10)))
                .launchDate(launch == null ? null : launch.toLocalDate())
                .isLive(rs.getBoolean(12))
                .lastNavDate(lastNav == null ? null : lastNav.toLocalDate())
                .build();
    }

    /**
     * Live Direct+Growth peer groups with their fund counts, biggest first within each asset class.
     * The structured form the category-browser endpoint returns.
     */
    public List<MfCategoryCount> categoryCounts() throws SQLException {
        String sql = "SELECT " + C_ASSET_CLASS + ", " + C_SUB_CATEGORY + ", COUNT(*) FROM " + T
                + " WHERE " + C_IS_LIVE + " = 1 AND " + C_PLAN + " = ? AND " + C_OPTION + " = ?"
                + " GROUP BY " + C_ASSET_CLASS + ", " + C_SUB_CATEGORY
                + " ORDER BY " + C_ASSET_CLASS + ", COUNT(*) DESC";

        List<MfCategoryCount> out = new ArrayList<>();
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, MfPlanOption.Plan.DIRECT.name());
            ps.setString(2, MfPlanOption.Option.GROWTH.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(new MfCategoryCount(rs.getString(1), rs.getString(2), rs.getInt(3)));
            }
        }
        return out;
    }

    /** Live scheme counts per (asset class, sub-category). Backs the category browser and sanity checks. */
    public Map<String, Integer> countByCategory() throws SQLException {
        String sql = "SELECT " + C_ASSET_CLASS + ", " + C_SUB_CATEGORY + ", COUNT(*) FROM " + T
                + " WHERE " + C_IS_LIVE + " = 1 AND " + C_PLAN + " = ? AND " + C_OPTION + " = ?"
                + " GROUP BY " + C_ASSET_CLASS + ", " + C_SUB_CATEGORY
                + " ORDER BY " + C_ASSET_CLASS + ", COUNT(*) DESC";

        Map<String, Integer> out = new LinkedHashMap<>();
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, MfPlanOption.Plan.DIRECT.name());
            ps.setString(2, MfPlanOption.Option.GROWTH.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.put(rs.getString(1) + " / " + rs.getString(2), rs.getInt(3));
                }
            }
        }
        return out;
    }
}
