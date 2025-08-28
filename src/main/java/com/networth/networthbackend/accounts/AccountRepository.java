package com.networth.networthbackend.accounts;

import com.networth.networthbackend.common.Constants;
import com.networth.networthbackend.common.JdbcRepository;
import com.networth.networthbackend.common.RowMapper;
import com.networth.networthbackend.db.Database;
import lombok.extern.slf4j.Slf4j;

import java.sql.*;

@Slf4j
public class AccountRepository extends JdbcRepository<Account> {

    private static final RowMapper<Account> MAPPER = rs -> Account.builder()
            .id(rs.getLong(Constants.DB.Accounts.COL_ID))
            .userId(rs.getLong(Constants.DB.Accounts.COL_USER_ID))
            .name(rs.getString(Constants.DB.Accounts.COL_NAME))
            .type(rs.getString(Constants.DB.Accounts.COL_TYPE))
            .isActive(rs.getBoolean(Constants.DB.Accounts.COL_IS_ACTIVE))
            .currency(rs.getString(Constants.DB.Accounts.COL_CURRENCY))
            .build();

    public boolean existsByUserIdAndNameAndType(long userId, String name, String type) throws SQLException {
        final String sql = Constants.SQL.SELECT + "1" + Constants.SQL.FROM + Constants.DB.Accounts.TABLE
                + Constants.SQL.WHERE + Constants.DB.Accounts.COL_USER_ID + Constants.SQL.EQ_PARAM
                + Constants.SQL.AND + Constants.DB.Accounts.COL_NAME + Constants.SQL.EQ_PARAM
                + Constants.SQL.AND + Constants.DB.Accounts.COL_TYPE + Constants.SQL.EQ_PARAM
                + Constants.SQL.LIMIT_1;
        try (Connection conn = Database.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setString(2, name);
            ps.setString(3, type);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public Account create(long userId, String name, String type, boolean isActive, String currency) throws SQLException {
        final String insertSql = Constants.SQL.INSERT_INTO + Constants.DB.Accounts.TABLE + Constants.SQL.OPEN_PAREN
                + String.join(Constants.SQL.COMMA_SPACE,
                Constants.DB.Accounts.COL_USER_ID,
                Constants.DB.Accounts.COL_NAME,
                Constants.DB.Accounts.COL_TYPE,
                Constants.DB.Accounts.COL_IS_ACTIVE,
                Constants.DB.Accounts.COL_CURRENCY)
                + Constants.SQL.CLOSE_PAREN + Constants.SQL.VALUES + Constants.SQL.OPEN_PAREN
                + String.join(Constants.SQL.COMMA_SPACE, Constants.SQL.PARAM, Constants.SQL.PARAM, Constants.SQL.PARAM, Constants.SQL.PARAM, Constants.SQL.PARAM)
                + Constants.SQL.CLOSE_PAREN;
        long id = 0L;
        try (Connection conn = Database.getConnection(); PreparedStatement ps = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, userId);
            ps.setString(2, name);
            ps.setString(3, type);
            ps.setBoolean(4, isActive);
            ps.setString(5, currency);
            int affected = ps.executeUpdate();
            log.debug(Constants.Logs.ACCOUNT_INSERT_AFFECTED, affected, userId, name, type);
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) id = rs.getLong(1);
            }
            // If account is not a BROKER type, create placeholder aggregate holding
            if (id > 0 && (!Constants.Accounts.TYPE_BROKER.equalsIgnoreCase(type))) {
                final String holdingSql = Constants.SQL.INSERT_INTO + Constants.DB.Holdings.TABLE + Constants.SQL.OPEN_PAREN
                        + String.join(Constants.SQL.COMMA_SPACE,
                        Constants.DB.Holdings.COL_ACCOUNT_ID,
                        Constants.DB.Holdings.COL_NAME,
                        Constants.DB.Holdings.COL_SYMBOL,
                        Constants.DB.Holdings.COL_UNITS)
                        + Constants.SQL.CLOSE_PAREN + Constants.SQL.VALUES + Constants.SQL.OPEN_PAREN
                        + String.join(Constants.SQL.COMMA_SPACE, Constants.SQL.PARAM, Constants.SQL.PARAM, Constants.SQL.PARAM, Constants.SQL.PARAM)
                        + Constants.SQL.CLOSE_PAREN;
                try (PreparedStatement psH = conn.prepareStatement(holdingSql)) {
                    psH.setLong(1, id);
                    psH.setString(2, Constants.Accounts.AGG_HOLDING_NAME);
                    psH.setString(3, Constants.Accounts.AGG_HOLDING_SYMBOL);
                    psH.setBigDecimal(4, new java.math.BigDecimal("0.0000"));
                    psH.executeUpdate();
                }
            }
        }
        return Account.builder().id(id).userId(userId).name(name).type(type).isActive(isActive).currency(currency).build();
    }

    public int deleteById(long id) throws SQLException {
        final String sql = Constants.SQL.DELETE_FROM + Constants.DB.Accounts.TABLE + Constants.SQL.WHERE + Constants.DB.Accounts.COL_ID + Constants.SQL.EQ_PARAM;
        return execute(sql, ps -> ps.setLong(1, id));
    }
}
