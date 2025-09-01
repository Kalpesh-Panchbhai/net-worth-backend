package com.networth.networthbackend.holdings;

import com.networth.networthbackend.common.Constants;
import com.networth.networthbackend.common.JdbcRepository;
import com.networth.networthbackend.db.Database;
import lombok.extern.slf4j.Slf4j;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class HoldingRepository extends JdbcRepository<Holding> {

    public boolean existsByAccountIdAndSymbol(long accountId, String symbol) throws SQLException {
        final String sql = Constants.SQL.SELECT + "1" + Constants.SQL.FROM + Constants.DB.Holdings.TABLE
                + Constants.SQL.WHERE + Constants.DB.Holdings.COL_ACCOUNT_ID + Constants.SQL.EQ_PARAM
                + Constants.SQL.AND + Constants.DB.Holdings.COL_SYMBOL + Constants.SQL.EQ_PARAM
                + Constants.SQL.LIMIT_1;
        try (Connection conn = Database.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, accountId);
            ps.setString(2, symbol);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public Holding create(long accountId, String name, String symbol) throws SQLException {
        // units column removed from holdings; keep param for backward API compatibility but ignore in insert
        final String sql = Constants.SQL.INSERT_INTO + Constants.DB.Holdings.TABLE + Constants.SQL.OPEN_PAREN
                + String.join(Constants.SQL.COMMA_SPACE,
                Constants.DB.Holdings.COL_ACCOUNT_ID,
                Constants.DB.Holdings.COL_NAME,
                Constants.DB.Holdings.COL_SYMBOL)
                + Constants.SQL.CLOSE_PAREN + Constants.SQL.VALUES + Constants.SQL.OPEN_PAREN
                + String.join(Constants.SQL.COMMA_SPACE, Constants.SQL.PARAM, Constants.SQL.PARAM, Constants.SQL.PARAM)
                + Constants.SQL.CLOSE_PAREN;
        long id = 0L;
        try (Connection conn = Database.getConnection(); PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, accountId);
            ps.setString(2, name);
            ps.setString(3, symbol);
            int affected = ps.executeUpdate();
            log.debug(Constants.Logs.HOLDING_INSERT_AFFECTED, affected, accountId, name, symbol);
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) id = rs.getLong(1);
            }
        }
        return Holding.builder().id(id).accountId(accountId).name(name).symbol(symbol).build();
    }

    public int deleteById(long id) throws SQLException {
        final String sql = Constants.SQL.DELETE_FROM + Constants.DB.Holdings.TABLE + Constants.SQL.WHERE + Constants.DB.Holdings.COL_ID + Constants.SQL.EQ_PARAM;
        return execute(sql, ps -> ps.setLong(1, id));
    }

    public List<HoldingSummary> listSummariesByAccountId(long accountId) throws SQLException {
        final String sql = Constants.DB.Holdings.SQL_LIST_SUMMARIES_BY_ACCOUNT_ID;
        try (Connection conn = Database.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, accountId);
            ps.setLong(2, accountId);
            ps.setLong(3, accountId);
            ps.setLong(4, accountId);
            ps.setLong(5, accountId);
            try (ResultSet rs = ps.executeQuery()) {
                List<HoldingSummary> list = new ArrayList<>();
                while (rs.next()) {
                    HoldingSummary s = HoldingSummary.builder()
                            .id(rs.getLong(Constants.DB.Holdings.COL_ID))
                            .accountId(rs.getLong(Constants.DB.Holdings.COL_ACCOUNT_ID))
                            .name(rs.getString(Constants.DB.Holdings.COL_NAME))
                            .symbol(rs.getString(Constants.DB.Holdings.COL_SYMBOL))
                            .currentDayValue(rs.getBigDecimal(Constants.DB.Aliases.COL_CURRENT_DAY_VALUE))
                            .previousDayValue(rs.getBigDecimal(Constants.DB.Aliases.COL_PREVIOUS_DAY_VALUE))
                            .invested(rs.getBigDecimal(Constants.DB.Aliases.COL_INVESTED))
                            .build();
                    list.add(s);
                }
                return list;
            }
        }
    }
}

