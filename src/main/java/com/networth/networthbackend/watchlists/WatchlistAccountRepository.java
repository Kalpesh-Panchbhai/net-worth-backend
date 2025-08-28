package com.networth.networthbackend.watchlists;

import com.networth.networthbackend.common.Constants;
import com.networth.networthbackend.common.JdbcRepository;
import com.networth.networthbackend.db.Database;
import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

@Slf4j
public class WatchlistAccountRepository extends JdbcRepository<Void> {

    public int link(long watchlistId, long accountId) throws SQLException {
        final String sql = Constants.SQL.INSERT_IGNORE_INTO + Constants.DB.WatchlistAccounts.TABLE
                + Constants.SQL.OPEN_PAREN + Constants.DB.WatchlistAccounts.COL_WATCHLIST_ID + Constants.SQL.COMMA_SPACE + Constants.DB.WatchlistAccounts.COL_ACCOUNT_ID + Constants.SQL.CLOSE_PAREN
                + Constants.SQL.VALUES + Constants.SQL.OPEN_PAREN + Constants.SQL.PARAM + Constants.SQL.COMMA_SPACE + Constants.SQL.PARAM + Constants.SQL.CLOSE_PAREN;
        try (Connection conn = Database.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, watchlistId);
            ps.setLong(2, accountId);
            int affected = ps.executeUpdate();
            log.debug(Constants.Logs.WATCHLIST_ACCOUNT_LINK_AFFECTED, affected, watchlistId, accountId);
            return affected;
        }
    }

    public int unlink(long watchlistId, long accountId) throws SQLException {
        final String sql = Constants.SQL.DELETE_FROM + Constants.DB.WatchlistAccounts.TABLE
                + Constants.SQL.WHERE + Constants.DB.WatchlistAccounts.COL_WATCHLIST_ID + Constants.SQL.EQ_PARAM
                + Constants.SQL.AND + Constants.DB.WatchlistAccounts.COL_ACCOUNT_ID + Constants.SQL.EQ_PARAM;
        try (Connection conn = Database.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, watchlistId);
            ps.setLong(2, accountId);
            int affected = ps.executeUpdate();
            log.debug(Constants.Logs.WATCHLIST_ACCOUNT_UNLINK_AFFECTED, affected, watchlistId, accountId);
            return affected;
        }
    }
}
