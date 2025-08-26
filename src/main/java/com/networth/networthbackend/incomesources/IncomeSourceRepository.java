package com.networth.networthbackend.incomesources;

import com.networth.networthbackend.common.Constants;
import com.networth.networthbackend.common.JdbcRepository;
import com.networth.networthbackend.common.RowMapper;
import com.networth.networthbackend.db.Database;
import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

@Slf4j
public class IncomeSourceRepository extends JdbcRepository<IncomeSource> {

    private static final RowMapper<IncomeSource> MAPPER = rs -> IncomeSource.builder()
            .id(rs.getLong(Constants.DB.IncomeSources.COL_ID))
            .userId(rs.getLong(Constants.DB.IncomeSources.COL_USER_ID))
            .name(rs.getString(Constants.DB.IncomeSources.COL_NAME))
            .isDefault(rs.getBoolean(Constants.DB.IncomeSources.COL_IS_DEFAULT))
            .build();

    public List<IncomeSource> listByUserId(long userId) throws SQLException {
        final String sql = Constants.SQL.SELECT
                + String.join(Constants.SQL.COMMA_SPACE,
                Constants.DB.IncomeSources.COL_ID,
                Constants.DB.IncomeSources.COL_USER_ID,
                Constants.DB.IncomeSources.COL_NAME,
                Constants.DB.IncomeSources.COL_IS_DEFAULT)
                + Constants.SQL.FROM + Constants.DB.IncomeSources.TABLE
                + Constants.SQL.WHERE + Constants.DB.IncomeSources.COL_USER_ID + Constants.SQL.EQ_PARAM;
        return queryList(sql, ps -> ps.setLong(1, userId), MAPPER);
    }

    public boolean existsByUserIdAndName(long userId, String name) throws SQLException {
        final String sql = Constants.SQL.SELECT + "1"
                + Constants.SQL.FROM + Constants.DB.IncomeSources.TABLE
                + Constants.SQL.WHERE + Constants.DB.IncomeSources.COL_USER_ID + Constants.SQL.EQ_PARAM
                + Constants.SQL.AND + Constants.DB.IncomeSources.COL_NAME + Constants.SQL.EQ_PARAM
                + Constants.SQL.LIMIT_1;
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setString(2, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public IncomeSource create(long userId, String name, boolean isDefault) throws SQLException {
        final String insertSql = Constants.SQL.INSERT_INTO + Constants.DB.IncomeSources.TABLE
                + Constants.SQL.OPEN_PAREN
                + String.join(Constants.SQL.COMMA_SPACE,
                Constants.DB.IncomeSources.COL_USER_ID,
                Constants.DB.IncomeSources.COL_NAME,
                Constants.DB.IncomeSources.COL_IS_DEFAULT)
                + Constants.SQL.CLOSE_PAREN
                + Constants.SQL.VALUES
                + Constants.SQL.OPEN_PAREN + String.join(Constants.SQL.COMMA_SPACE, Constants.SQL.PARAM, Constants.SQL.PARAM, Constants.SQL.PARAM) + Constants.SQL.CLOSE_PAREN;

        return inTx(conn -> {
            if (isDefault) {
                clearDefaults(conn, userId);
            }
            long id = 0L;
            try (PreparedStatement ps = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setLong(1, userId);
                ps.setString(2, name);
                ps.setBoolean(3, isDefault);
                int affected = ps.executeUpdate();
                log.debug(Constants.Logs.INCOME_SOURCE_INSERT_AFFECTED, affected, userId, name);
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        id = rs.getLong(1);
                    }
                }
            }
            return IncomeSource.builder()
                    .id(id)
                    .userId(userId)
                    .name(name)
                    .isDefault(isDefault)
                    .build();
        });
    }

    public boolean setDefault(long sourceId) throws SQLException {
        final String setDefaultSql = Constants.SQL.UPDATE + Constants.DB.IncomeSources.TABLE
                + Constants.SQL.SET + Constants.DB.IncomeSources.COL_IS_DEFAULT + Constants.SQL.EQ_PARAM
                + Constants.SQL.WHERE + Constants.DB.IncomeSources.COL_ID + Constants.SQL.EQ_PARAM;

        return inTx(conn -> {
            long userId = getUserIdForSource(conn, sourceId);
            if (userId <= 0) return false;
            clearDefaults(conn, userId);
            int affected;
            try (PreparedStatement set = conn.prepareStatement(setDefaultSql)) {
                set.setBoolean(1, true);
                set.setLong(2, sourceId);
                affected = set.executeUpdate();
            }
            log.debug(Constants.Logs.INCOME_SOURCE_SET_DEFAULT_AFFECTED, affected, sourceId);
            log.info(Constants.Logs.INCOME_SOURCE_SET_DEFAULT_DONE, sourceId, userId);
            return affected > 0;
        });
    }

    // ---------- Reusable internals

    @FunctionalInterface
    private interface TxWork<R> { R apply(Connection conn) throws SQLException; }

    private <R> R inTx(TxWork<R> work) throws SQLException {
        try (Connection conn = Database.getConnection()) {
            boolean initialAutoCommit = conn.getAutoCommit();
            try {
                conn.setAutoCommit(false);
                R result = work.apply(conn);
                conn.commit();
                return result;
            } catch (SQLException e) {
                try { conn.rollback(); } catch (SQLException ignored) {}
                throw e;
            } finally {
                try { conn.setAutoCommit(initialAutoCommit); } catch (SQLException ignored) {}
            }
        }
    }

    private void clearDefaults(Connection conn, long userId) throws SQLException {
        final String clearDefaultsSql = Constants.SQL.UPDATE + Constants.DB.IncomeSources.TABLE
                + Constants.SQL.SET + Constants.DB.IncomeSources.COL_IS_DEFAULT + Constants.SQL.EQ_PARAM
                + Constants.SQL.WHERE + Constants.DB.IncomeSources.COL_USER_ID + Constants.SQL.EQ_PARAM
                + Constants.SQL.AND + Constants.DB.IncomeSources.COL_IS_DEFAULT + Constants.SQL.EQ_PARAM;
        try (PreparedStatement clear = conn.prepareStatement(clearDefaultsSql)) {
            clear.setBoolean(1, false);
            clear.setLong(2, userId);
            clear.setBoolean(3, true);
            int cleared = clear.executeUpdate();
            log.debug(Constants.Logs.INCOME_SOURCE_CLEAR_DEFAULTS_AFFECTED, cleared, userId);
        }
    }

    private long getUserIdForSource(Connection conn, long sourceId) throws SQLException {
        final String selectUserSql = Constants.SQL.SELECT + Constants.DB.IncomeSources.COL_USER_ID
                + Constants.SQL.FROM + Constants.DB.IncomeSources.TABLE
                + Constants.SQL.WHERE + Constants.DB.IncomeSources.COL_ID + Constants.SQL.EQ_PARAM
                + Constants.SQL.LIMIT_1;
        try (PreparedStatement ps = conn.prepareStatement(selectUserSql)) {
            ps.setLong(1, sourceId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return -1L;
                return rs.getLong(1);
            }
        }
    }
}
