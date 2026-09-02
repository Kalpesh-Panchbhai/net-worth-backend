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

    public Optional<Watchlist> findByUserIdAndName(long userId, String name) throws SQLException {
        final String sql = Constants.SQL.SELECT + String.join(Constants.SQL.COMMA_SPACE, Constants.DB.Watchlists.COL_ID, Constants.DB.Watchlists.COL_USER_ID, Constants.DB.Watchlists.COL_NAME) + Constants.SQL.FROM + Constants.DB.Watchlists.TABLE + Constants.SQL.WHERE + Constants.DB.Watchlists.COL_USER_ID + Constants.SQL.EQ_PARAM + Constants.SQL.AND + Constants.DB.Watchlists.COL_NAME + Constants.SQL.EQ_PARAM + Constants.SQL.LIMIT_1;
        return queryOne(sql, ps -> {
            ps.setLong(1, userId);
            ps.setString(2, name);
        }, MAPPER);
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

    // Per-(watchlist, member account) native-currency breakdown, so amounts can be converted to
    // the display currency per account before being summed. Uses one row per linked account
    // (or a single null-account row for empty watchlists so they still appear).
    //
    // Both daily CTEs drive from member_accounts, so the range scan on
    // account_holding_values_daily is bounded by the watchlists in scope via idx_ahvd_account_date
    // rather than starting from the whole table.
    //
    // acct_latest is deliberately left as a bare single-table GROUP BY. Joining member_accounts
    // *inside* it looks tighter but is far slower: MySQL's loose index scan
    // ("Using index for group-by") only applies to a single table, so the join turns an index-only
    // group-by into a full scan. Group first on the index, then scope by joining the result.
    private static final String ACCOUNT_DAILY_CTES = """
                member_accounts AS (
                    SELECT DISTINCT account_id FROM wl_accounts WHERE account_id IS NOT NULL
                ),
                acct_daily AS (
                    SELECT ahvd.account_id,
                           SUM(CASE WHEN ahvd.date = CURRENT_DATE THEN ahvd.value ELSE 0 END) AS current_day_value,
                           SUM(CASE WHEN ahvd.date = CURRENT_DATE - INTERVAL 1 DAY THEN ahvd.value ELSE 0 END) AS previous_day_value
                    FROM member_accounts ma
                    STRAIGHT_JOIN account_holding_values_daily ahvd
                         ON ahvd.account_id = ma.account_id
                        AND ahvd.date IN (CURRENT_DATE, CURRENT_DATE - INTERVAL 1 DAY)
                    GROUP BY ahvd.account_id
                ),
                acct_latest AS (
                    SELECT account_id, holding_id, MAX(date) AS max_date
                    FROM account_holding_values_daily
                    GROUP BY account_id, holding_id
                ),
                acct_invested AS (
                    SELECT ai.account_id, SUM(ai.invested) AS invested
                    FROM member_accounts ma
                    INNER JOIN acct_latest m ON m.account_id = ma.account_id
                    INNER JOIN account_holding_values_daily ai
                            ON ai.account_id = m.account_id
                           AND ai.holding_id = m.holding_id
                           AND ai.date = m.max_date
                    GROUP BY ai.account_id
                )
            """;

    private static final String SELECT_ACCOUNT_BREAKDOWN = """
                SELECT wla.watchlist_id, wla.user_id, wla.watchlist_name, wla.account_id, wla.currency,
                       wla.type, wla.needs_daily_data,
                       COALESCE(ad.current_day_value, CAST(0 AS DECIMAL(24,4))) AS current_day_value,
                       COALESCE(ad.previous_day_value, CAST(0 AS DECIMAL(24,4))) AS previous_day_value,
                       COALESCE(ai.invested, CAST(0 AS DECIMAL(24,4))) AS invested
                FROM wl_accounts wla
                LEFT JOIN acct_daily ad ON ad.account_id = wla.account_id
                LEFT JOIN acct_invested ai ON ai.account_id = wla.account_id
                ORDER BY wla.watchlist_id
            """;

    private static final String SQL_ACCOUNT_BREAKDOWN_BY_USER_ID = "WITH user_watchlists AS (\n"
            + "    SELECT id, user_id, name FROM watchlists WHERE user_id = ?\n"
            + "),\n"
            + "wl_accounts AS (\n"
            + "    SELECT uw.id AS watchlist_id, uw.user_id AS user_id, uw.name AS watchlist_name,\n"
            + "           a.id AS account_id, a.currency AS currency,\n"
            + "           a.type AS type, a.needs_daily_data AS needs_daily_data\n"
            + "    FROM user_watchlists uw\n"
            + "    LEFT JOIN watchlist_accounts wa ON wa.watchlist_id = uw.id\n"
            + "    LEFT JOIN accounts a ON a.id = wa.account_id\n"
            + "),\n"
            + ACCOUNT_DAILY_CTES
            + SELECT_ACCOUNT_BREAKDOWN;

    private static final String SQL_ACCOUNT_BREAKDOWN_BY_ACCOUNT_ID = "WITH target_watchlists AS (\n"
            + "    SELECT DISTINCT w.id, w.user_id, w.name\n"
            + "    FROM watchlists w\n"
            + "    JOIN watchlist_accounts wa ON wa.watchlist_id = w.id\n"
            + "    WHERE wa.account_id = ?\n"
            + "),\n"
            + "wl_accounts AS (\n"
            + "    SELECT tw.id AS watchlist_id, tw.user_id AS user_id, tw.name AS watchlist_name,\n"
            + "           a.id AS account_id, a.currency AS currency,\n"
            + "           a.type AS type, a.needs_daily_data AS needs_daily_data\n"
            + "    FROM target_watchlists tw\n"
            + "    LEFT JOIN watchlist_accounts wa ON wa.watchlist_id = tw.id\n"
            + "    LEFT JOIN accounts a ON a.id = wa.account_id\n"
            + "),\n"
            + ACCOUNT_DAILY_CTES
            + SELECT_ACCOUNT_BREAKDOWN;

    public List<WatchlistAccountRow> listAccountBreakdownByUserId(long userId) throws SQLException {
        return accountBreakdown(SQL_ACCOUNT_BREAKDOWN_BY_USER_ID, userId);
    }

    public List<WatchlistAccountRow> listAccountBreakdownByAccountId(long accountId) throws SQLException {
        return accountBreakdown(SQL_ACCOUNT_BREAKDOWN_BY_ACCOUNT_ID, accountId);
    }

    private List<WatchlistAccountRow> accountBreakdown(String sql, long id) throws SQLException {
        try (Connection conn = Database.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                List<WatchlistAccountRow> rows = new ArrayList<>();
                while (rs.next()) {
                    // wasNull() reports the most recently read column, so account_id must be read
                    // and tested before anything else is pulled off the row.
                    long accountIdValue = rs.getLong("account_id");
                    Long accountId = rs.wasNull() ? null : accountIdValue;
                    WatchlistAccountRow row = WatchlistAccountRow.builder()
                            .accountId(accountId)
                            .watchlistId(rs.getLong("watchlist_id"))
                            .userId(rs.getLong("user_id"))
                            .watchlistName(rs.getString("watchlist_name"))
                            .currency(rs.getString("currency"))
                            .type(rs.getString("type"))
                            .needsDailyData(rs.getBoolean("needs_daily_data"))
                            .currentDayValue(rs.getBigDecimal(Constants.DB.Aliases.COL_CURRENT_DAY_VALUE))
                            .previousDayValue(rs.getBigDecimal(Constants.DB.Aliases.COL_PREVIOUS_DAY_VALUE))
                            .invested(rs.getBigDecimal(Constants.DB.Aliases.COL_INVESTED))
                            .build();
                    rows.add(row);
                }
                return rows;
            }
        }
    }
}
