package com.networth.networthbackend.incometags;

import com.networth.networthbackend.common.Constants;
import com.networth.networthbackend.common.JdbcRepository;
import com.networth.networthbackend.common.RowMapper;
import lombok.extern.slf4j.Slf4j;

import java.sql.SQLException;
import java.util.List;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.ResultSet;
import com.networth.networthbackend.db.Database;

@Slf4j
public class IncomeTagRepository extends JdbcRepository<IncomeTag> {

    private static final RowMapper<IncomeTag> MAPPER = rs -> IncomeTag.builder()
            .id(rs.getLong(Constants.DB.IncomeTags.COL_ID))
            .userId(rs.getLong(Constants.DB.IncomeTags.COL_USER_ID))
            .name(rs.getString(Constants.DB.IncomeTags.COL_NAME))
            .isDefault(rs.getBoolean(Constants.DB.IncomeTags.COL_IS_DEFAULT))
            .build();

    public List<IncomeTag> listByUserId(long userId) throws SQLException {
        final String sql = Constants.SQL.SELECT
                + String.join(Constants.SQL.COMMA_SPACE,
                        Constants.DB.IncomeTags.COL_ID,
                        Constants.DB.IncomeTags.COL_USER_ID,
                        Constants.DB.IncomeTags.COL_NAME,
                        Constants.DB.IncomeTags.COL_IS_DEFAULT)
                + Constants.SQL.FROM + Constants.DB.IncomeTags.TABLE
                + Constants.SQL.WHERE + Constants.DB.IncomeTags.COL_USER_ID + Constants.SQL.EQ_PARAM;
        return queryList(sql, ps -> ps.setLong(1, userId), MAPPER);
    }

    public boolean existsByUserIdAndName(long userId, String name) throws SQLException {
        final String sql = Constants.SQL.SELECT + "1"
                + Constants.SQL.FROM + Constants.DB.IncomeTags.TABLE
                + Constants.SQL.WHERE + Constants.DB.IncomeTags.COL_USER_ID + Constants.SQL.EQ_PARAM
                + Constants.SQL.AND + Constants.DB.IncomeTags.COL_NAME + Constants.SQL.EQ_PARAM
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

    public IncomeTag create(long userId, String name, boolean isDefault) throws SQLException {
        final String insertSql = Constants.SQL.INSERT_INTO + Constants.DB.IncomeTags.TABLE
                + Constants.SQL.OPEN_PAREN
                + String.join(Constants.SQL.COMMA_SPACE,
                    Constants.DB.IncomeTags.COL_USER_ID,
                    Constants.DB.IncomeTags.COL_NAME,
                    Constants.DB.IncomeTags.COL_IS_DEFAULT)
                + Constants.SQL.CLOSE_PAREN
                + Constants.SQL.VALUES
                + Constants.SQL.OPEN_PAREN + String.join(Constants.SQL.COMMA_SPACE, Constants.SQL.PARAM, Constants.SQL.PARAM, Constants.SQL.PARAM) + Constants.SQL.CLOSE_PAREN;

        final String clearDefaultsSql = Constants.SQL.UPDATE + Constants.DB.IncomeTags.TABLE
                + Constants.SQL.SET + Constants.DB.IncomeTags.COL_IS_DEFAULT + Constants.SQL.EQ_PARAM
                + Constants.SQL.WHERE + Constants.DB.IncomeTags.COL_USER_ID + Constants.SQL.EQ_PARAM
                + Constants.SQL.AND + Constants.DB.IncomeTags.COL_IS_DEFAULT + Constants.SQL.EQ_PARAM;

        try (Connection conn = Database.getConnection()) {
            boolean initialAutoCommit = conn.getAutoCommit();
            try {
                conn.setAutoCommit(false);
                if (isDefault) {
                    try (PreparedStatement clear = conn.prepareStatement(clearDefaultsSql)) {
                        clear.setBoolean(1, false);
                        clear.setLong(2, userId);
                        clear.setBoolean(3, true);
                        int cleared = clear.executeUpdate();
                        log.debug(Constants.Logs.INCOME_TAG_CLEAR_DEFAULTS_AFFECTED, cleared, userId);
                    }
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

                conn.commit();
                return IncomeTag.builder()
                        .id(id)
                        .userId(userId)
                        .name(name)
                        .isDefault(isDefault)
                        .build();
            } catch (SQLException e) {
                try { conn.rollback(); } catch (SQLException ignored) {}
                throw e;
            } finally {
                try { conn.setAutoCommit(initialAutoCommit); } catch (SQLException ignored) {}
            }
        }
    }
}
