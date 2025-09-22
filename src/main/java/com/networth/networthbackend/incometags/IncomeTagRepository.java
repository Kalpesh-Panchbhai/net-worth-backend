package com.networth.networthbackend.incometags;

import com.networth.networthbackend.common.Constants;
import com.networth.networthbackend.common.JdbcRepository;
import com.networth.networthbackend.common.RowMapper;
import com.networth.networthbackend.db.Database;
import lombok.extern.slf4j.Slf4j;

import java.sql.*;
import java.util.List;

@Slf4j
public class IncomeTagRepository extends JdbcRepository<IncomeTag> {

    private static final RowMapper<IncomeTag> MAPPER = rs -> IncomeTag.builder().id(rs.getLong(Constants.DB.IncomeTags.COL_ID)).userId(rs.getLong(Constants.DB.IncomeTags.COL_USER_ID)).name(rs.getString(Constants.DB.IncomeTags.COL_NAME)).isDefault(rs.getBoolean(Constants.DB.IncomeTags.COL_IS_DEFAULT)).build();

    public List<IncomeTag> listByUserId(long userId) throws SQLException {
        final String sql = Constants.SQL.SELECT + String.join(Constants.SQL.COMMA_SPACE, Constants.DB.IncomeTags.COL_ID, Constants.DB.IncomeTags.COL_USER_ID, Constants.DB.IncomeTags.COL_NAME, Constants.DB.IncomeTags.COL_IS_DEFAULT) + Constants.SQL.FROM + Constants.DB.IncomeTags.TABLE + Constants.SQL.WHERE + Constants.DB.IncomeTags.COL_USER_ID + Constants.SQL.EQ_PARAM;
        return queryList(sql, ps -> ps.setLong(1, userId), MAPPER);
    }

    public IncomeTag create(long userId, String name, boolean isDefault) throws SQLException {
        final String insertSql = Constants.SQL.INSERT_INTO + Constants.DB.IncomeTags.TABLE + Constants.SQL.OPEN_PAREN + String.join(Constants.SQL.COMMA_SPACE, Constants.DB.IncomeTags.COL_USER_ID, Constants.DB.IncomeTags.COL_NAME, Constants.DB.IncomeTags.COL_IS_DEFAULT) + Constants.SQL.CLOSE_PAREN + Constants.SQL.VALUES + Constants.SQL.OPEN_PAREN + String.join(Constants.SQL.COMMA_SPACE, Constants.SQL.PARAM, Constants.SQL.PARAM, Constants.SQL.PARAM) + Constants.SQL.CLOSE_PAREN;

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
                log.debug(Constants.Logs.INCOME_TAG_INSERT_AFFECTED, affected, userId, name);
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        id = rs.getLong(1);
                    }
                }
            }
            return IncomeTag.builder().id(id).userId(userId).name(name).isDefault(isDefault).build();
        });
    }

    public boolean setDefault(long tagId) throws SQLException {
        final String setDefaultSql = Constants.SQL.UPDATE + Constants.DB.IncomeTags.TABLE + Constants.SQL.SET + Constants.DB.IncomeTags.COL_IS_DEFAULT + Constants.SQL.EQ_PARAM + Constants.SQL.WHERE + Constants.DB.IncomeTags.COL_ID + Constants.SQL.EQ_PARAM;

        return inTx(conn -> {
            long userId = getUserIdForTag(conn, tagId);
            if (userId <= 0) return false;
            clearDefaults(conn, userId);
            int affected;
            try (PreparedStatement set = conn.prepareStatement(setDefaultSql)) {
                set.setBoolean(1, true);
                set.setLong(2, tagId);
                affected = set.executeUpdate();
            }
            log.debug(Constants.Logs.INCOME_TAG_SET_DEFAULT_AFFECTED, affected, tagId);
            log.info(Constants.Logs.INCOME_TAG_SET_DEFAULT_DONE, tagId, userId);
            return affected > 0;
        });
    }

    // ---------- Reusable internals

    private <R> R inTx(TxWork<R> work) throws SQLException {
        try (Connection conn = Database.getConnection()) {
            boolean initialAutoCommit = conn.getAutoCommit();
            try {
                conn.setAutoCommit(false);
                R result = work.apply(conn);
                conn.commit();
                return result;
            } catch (SQLException e) {
                try {
                    conn.rollback();
                } catch (SQLException ignored) {
                }
                throw e;
            } finally {
                try {
                    conn.setAutoCommit(initialAutoCommit);
                } catch (SQLException ignored) {
                }
            }
        }
    }

    private void clearDefaults(Connection conn, long userId) throws SQLException {
        final String clearDefaultsSql = Constants.SQL.UPDATE + Constants.DB.IncomeTags.TABLE + Constants.SQL.SET + Constants.DB.IncomeTags.COL_IS_DEFAULT + Constants.SQL.EQ_PARAM + Constants.SQL.WHERE + Constants.DB.IncomeTags.COL_USER_ID + Constants.SQL.EQ_PARAM + Constants.SQL.AND + Constants.DB.IncomeTags.COL_IS_DEFAULT + Constants.SQL.EQ_PARAM;
        try (PreparedStatement clear = conn.prepareStatement(clearDefaultsSql)) {
            clear.setBoolean(1, false);
            clear.setLong(2, userId);
            clear.setBoolean(3, true);
            int cleared = clear.executeUpdate();
            log.debug(Constants.Logs.INCOME_TAG_CLEAR_DEFAULTS_AFFECTED, cleared, userId);
        }
    }

    private long getUserIdForTag(Connection conn, long tagId) throws SQLException {
        final String selectUserSql = Constants.SQL.SELECT + Constants.DB.IncomeTags.COL_USER_ID + Constants.SQL.FROM + Constants.DB.IncomeTags.TABLE + Constants.SQL.WHERE + Constants.DB.IncomeTags.COL_ID + Constants.SQL.EQ_PARAM + Constants.SQL.LIMIT_1;
        try (PreparedStatement ps = conn.prepareStatement(selectUserSql)) {
            ps.setLong(1, tagId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return -1L;
                return rs.getLong(1);
            }
        }
    }

    @FunctionalInterface
    private interface TxWork<R> {
        R apply(Connection conn) throws SQLException;
    }
}
