package com.networth.networthbackend.watchlists;

import com.networth.networthbackend.common.Constants;
import com.networth.networthbackend.common.JdbcRepository;
import com.networth.networthbackend.common.RowMapper;
import com.networth.networthbackend.db.Database;
import lombok.extern.slf4j.Slf4j;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
public class WatchlistRepository extends JdbcRepository<Watchlist> {

    private static final RowMapper<Watchlist> MAPPER = rs -> Watchlist.builder().id(rs.getLong(Constants.DB.Watchlists.COL_ID)).userId(rs.getLong(Constants.DB.Watchlists.COL_USER_ID)).name(rs.getString(Constants.DB.Watchlists.COL_NAME)).build();

    public Optional<Watchlist> findById(long id) throws SQLException {
        final String sql = Constants.SQL.SELECT + String.join(Constants.SQL.COMMA_SPACE, Constants.DB.Watchlists.COL_ID, Constants.DB.Watchlists.COL_USER_ID, Constants.DB.Watchlists.COL_NAME) + Constants.SQL.FROM + Constants.DB.Watchlists.TABLE + Constants.SQL.WHERE + Constants.DB.Watchlists.COL_ID + Constants.SQL.EQ_PARAM + Constants.SQL.LIMIT_1;
        return queryOne(sql, ps -> ps.setLong(1, id), MAPPER);
    }

    public boolean existsByUserIdAndName(long userId, String name) throws SQLException {
        final String sql = Constants.SQL.SELECT + "1" + Constants.SQL.FROM + Constants.DB.Watchlists.TABLE + Constants.SQL.WHERE + Constants.DB.Watchlists.COL_USER_ID + Constants.SQL.EQ_PARAM + Constants.SQL.AND + Constants.DB.Watchlists.COL_NAME + Constants.SQL.EQ_PARAM + Constants.SQL.LIMIT_1;
        try (Connection conn = Database.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setString(2, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public Watchlist create(long userId, String name) throws SQLException {
        final String insertSql = Constants.SQL.INSERT_INTO + Constants.DB.Watchlists.TABLE + Constants.SQL.OPEN_PAREN + String.join(Constants.SQL.COMMA_SPACE, Constants.DB.Watchlists.COL_USER_ID, Constants.DB.Watchlists.COL_NAME) + Constants.SQL.CLOSE_PAREN + Constants.SQL.VALUES + Constants.SQL.OPEN_PAREN + String.join(Constants.SQL.COMMA_SPACE, Constants.SQL.PARAM, Constants.SQL.PARAM) + Constants.SQL.CLOSE_PAREN;
        long id = 0L;
        try (Connection conn = Database.getConnection(); PreparedStatement ps = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, userId);
            ps.setString(2, name);
            int affected = ps.executeUpdate();
            log.debug(Constants.Logs.WATCHLIST_INSERT_AFFECTED, affected, userId, name);
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) id = rs.getLong(1);
            }
        }
        return Watchlist.builder().id(id).userId(userId).name(name).build();
    }

    public int updateName(long id, String newName) throws SQLException {
        final String sql = Constants.SQL.UPDATE + Constants.DB.Watchlists.TABLE + Constants.SQL.SET + Constants.DB.Watchlists.COL_NAME + Constants.SQL.EQ_PARAM + Constants.SQL.WHERE + Constants.DB.Watchlists.COL_ID + Constants.SQL.EQ_PARAM;
        return execute(sql, ps -> {
            ps.setString(1, newName);
            ps.setLong(2, id);
        });
    }

    public int deleteById(long id) throws SQLException {
        final String sql = Constants.SQL.DELETE_FROM + Constants.DB.Watchlists.TABLE + Constants.SQL.WHERE + Constants.DB.Watchlists.COL_ID + Constants.SQL.EQ_PARAM;
        return execute(sql, ps -> ps.setLong(1, id));
    }

    public List<WatchlistSummary> listSummariesByUserId(long userId) throws SQLException {
        final String sql = Constants.DB.Watchlists.SQL_LIST_SUMMARIES_BY_USER_ID;
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setLong(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                List<WatchlistSummary> list = new ArrayList<>();
                while (rs.next()) {
                    WatchlistSummary s = WatchlistSummary.builder()
                            .id(rs.getLong("id"))
                            .userId(rs.getLong("user_id"))
                            .name(rs.getString("name"))
                            .currentDayValue(rs.getBigDecimal("current_day_value"))
                            .previousDayValue(rs.getBigDecimal("previous_day_value"))
                            .invested(rs.getBigDecimal("invested"))
                            .build();
                    list.add(s);
                }
                return list;
            }
        }
    }
}
