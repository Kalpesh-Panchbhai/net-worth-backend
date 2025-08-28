package com.networth.networthbackend.accounts;

import com.networth.networthbackend.common.Constants;
import com.networth.networthbackend.common.JdbcRepository;
import com.networth.networthbackend.common.RowMapper;
import com.networth.networthbackend.db.Database;
import lombok.extern.slf4j.Slf4j;

import java.sql.*;
import java.util.Optional;

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

    public Optional<Account> findById(long id) throws SQLException {
        final String sql = Constants.SQL.SELECT + String.join(Constants.SQL.COMMA_SPACE,
                Constants.DB.Accounts.COL_ID,
                Constants.DB.Accounts.COL_USER_ID,
                Constants.DB.Accounts.COL_NAME,
                Constants.DB.Accounts.COL_TYPE,
                Constants.DB.Accounts.COL_IS_ACTIVE,
                Constants.DB.Accounts.COL_CURRENCY)
                + Constants.SQL.FROM + Constants.DB.Accounts.TABLE
                + Constants.SQL.WHERE + Constants.DB.Accounts.COL_ID + Constants.SQL.EQ_PARAM
                + Constants.SQL.LIMIT_1;
        return queryOne(sql, ps -> ps.setLong(1, id), MAPPER);
    }

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

    public boolean existsByUserIdAndNameAndTypeExceptId(long userId, String name, String type, long excludeId) throws SQLException {
        final String sql = Constants.SQL.SELECT + "1" + Constants.SQL.FROM + Constants.DB.Accounts.TABLE
                + Constants.SQL.WHERE + Constants.DB.Accounts.COL_USER_ID + Constants.SQL.EQ_PARAM
                + Constants.SQL.AND + Constants.DB.Accounts.COL_NAME + Constants.SQL.EQ_PARAM
                + Constants.SQL.AND + Constants.DB.Accounts.COL_TYPE + Constants.SQL.EQ_PARAM
                + Constants.SQL.AND + Constants.DB.Accounts.COL_ID + " <> ?"
                + Constants.SQL.LIMIT_1;
        try (Connection conn = Database.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setString(2, name);
            ps.setString(3, type);
            ps.setLong(4, excludeId);
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

    public int updateNameAndIsActive(long id, String newNameNullable, Boolean isActiveNullable) throws SQLException {
        StringBuilder sb = new StringBuilder();
        sb.append(Constants.SQL.UPDATE).append(Constants.DB.Accounts.TABLE).append(Constants.SQL.SET);
        boolean first = true;
        if (newNameNullable != null) {
            sb.append(Constants.DB.Accounts.COL_NAME).append(Constants.SQL.EQ_PARAM);
            first = false;
        }
        if (isActiveNullable != null) {
            if (!first) sb.append(Constants.SQL.COMMA_SPACE);
            sb.append(Constants.DB.Accounts.COL_IS_ACTIVE).append(Constants.SQL.EQ_PARAM);
        }
        sb.append(Constants.SQL.WHERE).append(Constants.DB.Accounts.COL_ID).append(Constants.SQL.EQ_PARAM);
        final String sql = sb.toString();
        return execute(sql, ps -> {
            int idx = 1;
            if (newNameNullable != null) ps.setString(idx++, newNameNullable);
            if (isActiveNullable != null) ps.setBoolean(idx++, isActiveNullable);
            ps.setLong(idx, id);
        });
    }
}
