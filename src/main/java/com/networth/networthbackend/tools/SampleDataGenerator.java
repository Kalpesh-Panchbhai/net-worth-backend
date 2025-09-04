package com.networth.networthbackend.tools;

import com.networth.networthbackend.common.Constants;
import com.networth.networthbackend.db.Database;

import java.math.BigDecimal;
import java.sql.*;
import java.sql.Date;
import java.time.LocalDate;
import java.util.*;

/**
 * Sample data generator for performance testing.
 * <p>
 * Creates per user:
 * - N accounts (broker and non-broker)
 * - Holdings for broker accounts
 * - Watchlists and membership for all accounts
 * - Daily time-series in account_holding_values_daily:
 * - For non-broker accounts: account-level rows (via aggregate holding)
 * - For each holding: holding-level rows (holding_id = holding.id)
 * <p>
 * Customizable via CLI args (key=value):
 * externalUserId=demo-user                  (single user; backwards compatible)
 * externalUserIds=u1,u2,u3                  (multiple users; comma-separated)
 * users=5 userPrefix=demo-user-             (alternatively, generate users demo-user-1..5)
 * accountsTotal=10
 * brokerAccounts=5
 * holdingsPerBroker=5
 * watchlists=5
 * days=365
 * endDate=TODAY  (YYYY-MM-DD). If omitted, uses current date.
 * currency=INR
 * seed=42
 * autoCommit=false                          (better throughput when false)
 * batchSize=2000                            (flush batch inserts every N rows)
 * disableFks=true                           (temporarily disable FK checks for speed)
 * disableUniqueChecks=true                  (temporarily disable unique checks)
 * commitPerBatch=false                      (commit after every batch flush)
 * <p>
 * Example:
 * java -cp target/net-worth-backend-*-jar-with-dependencies.jar \
 * com.networth.networthbackend.tools.SampleDataGenerator \
 * externalUserIds=user1,user2 accountsTotal=10 brokerAccounts=5 holdingsPerBroker=5 watchlists=5 days=365
 */
public class SampleDataGenerator {

    public static void main(String[] args) throws Exception {
        Config cfg = Config.fromArgs(args);

        if (cfg.brokerAccounts > cfg.accountsTotal) {
            throw new IllegalArgumentException("brokerAccounts cannot exceed accountsTotal");
        }

        // no-op: verbose logs removed
        try (Connection conn = Database.getConnection()) {
            boolean initialAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(cfg.autoCommit);
            // no-op: verbose logs removed
            try {
                // Session optimizations for bulk load
                if (cfg.disableFks) trySetSessionVar(conn, "FOREIGN_KEY_CHECKS", false, false);
                if (cfg.disableUniqueChecks) trySetSessionVar(conn, "UNIQUE_CHECKS", false, false);
                for (int idx = 0; idx < cfg.externalUserIds.size(); idx++) {
                    processUser(conn, cfg, cfg.externalUserIds.get(idx), idx);
                }
                if (!cfg.autoCommit) conn.commit();
            } catch (Exception e) {
                try {
                    if (!cfg.autoCommit) conn.rollback();
                } catch (SQLException ignored) {
                }
                throw e;
            } finally {
                // Re-enable session checks if we disabled them
                if (cfg.disableUniqueChecks) trySetSessionVar(conn, "UNIQUE_CHECKS", true, false);
                if (cfg.disableFks) trySetSessionVar(conn, "FOREIGN_KEY_CHECKS", true, false);
                try {
                    conn.setAutoCommit(initialAutoCommit);
                } catch (SQLException ignored) {
                }
            }
            // no-op: verbose logs removed
        }
    }

    // Orchestrates generation for a single external user
    private static void processUser(Connection conn, Config cfg, String externalUserId, int idx) throws Exception {
        long userId = ensureUser(conn, externalUserId, false);
        List<Long> accountIds = createAccounts(conn, userId, cfg.accountsTotal, cfg.brokerAccounts, cfg.currency, cfg.commitPerBatch, false);
        // Split into broker vs non-broker
        List<Long> brokerAccountIds = accountIds.subList(0, cfg.brokerAccounts);
        List<Long> nonBrokerAccountIds = accountIds.subList(cfg.brokerAccounts, accountIds.size());
        // Filter non-broker accounts that actually need daily data (drives whether we create agg holding)
        List<Long> nonBrokerAccountsNeedingDaily = filterAccountsNeedingDailyData(conn, nonBrokerAccountIds);
        // Holdings for broker accounts
        Map<Long, List<Long>> accountHoldings = createHoldingsForBrokerAccounts(conn, brokerAccountIds, cfg.holdingsPerBroker, cfg.commitPerBatch, false);
        // Aggregate holding for ALL non-broker accounts (placeholder exists regardless of needsDailyData)
        Map<Long, Long> nonBrokerAggHoldingAll = createAggregateHoldingForAccounts(conn, nonBrokerAccountIds, cfg.commitPerBatch, false);
        // Filtered view for accounts that need daily data (used for time-series and transactions to keep volume bounded)
        Map<Long, Long> nonBrokerAggHolding = new LinkedHashMap<>();
        for (long aId : nonBrokerAccountsNeedingDaily) {
            Long hId = nonBrokerAggHoldingAll.get(aId);
            if (hId != null) nonBrokerAggHolding.put(aId, hId);
        }
        // Watchlists & memberships
        List<Long> watchlistIds = createWatchlists(conn, userId, cfg.watchlists, cfg.commitPerBatch, false);
        attachAccountsToWatchlists(conn, watchlistIds, accountIds, cfg.commitPerBatch, false);
        // Income tags, sources, and sample incomes
        List<Long> incomeTagIds = createIncomeTags(conn, userId, cfg.commitPerBatch, false);
        List<Long> incomeSourceIds = createIncomeSources(conn, userId, cfg.commitPerBatch, false);
        generateSampleIncomes(conn, userId, incomeSourceIds, incomeTagIds, cfg.currency, cfg.endDate, 10, cfg.commitPerBatch, false);
        // Time-series: non-broker accounts needing daily data
        generateAccountDailyForNonBroker(conn, nonBrokerAccountsNeedingDaily, nonBrokerAggHolding, cfg.startDate, cfg.endDate, cfg.currency, cfg.seed + (idx * 1000L), cfg.batchSize, cfg.commitPerBatch, false);
        // Time-series: broker holdings
        generateHoldingDaily(conn, accountHoldings, cfg.startDate, cfg.endDate, cfg.currency, cfg.seed + 1 + (idx * 1000L), cfg.batchSize, cfg.commitPerBatch, false);
        // Transactions: create 10 per holding (including aggregate holdings for ALL non-broker accounts)
        generateSampleTransactions(conn, accountHoldings, nonBrokerAggHoldingAll, cfg.endDate, 10, cfg.batchSize, cfg.commitPerBatch, false);
        conn.commit();
    }

    private static long ensureUser(Connection conn, String externalUserId, boolean verbose) throws SQLException {
        // Try select
        try (PreparedStatement ps = conn.prepareStatement(
                Constants.SQL.SELECT + Constants.DB.Users.COL_ID +
                        Constants.SQL.FROM + Constants.DB.Users.TABLE +
                        Constants.SQL.WHERE + Constants.DB.Users.COL_EXTERNAL_USER_ID + Constants.SQL.EQ_PARAM +
                        Constants.SQL.LIMIT_1)) {
            ps.setString(1, externalUserId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    long id = rs.getLong(1);
                    return id;
                }
            }
        }
        // Insert
        try (PreparedStatement ps = conn.prepareStatement(
                Constants.SQL.INSERT_INTO + Constants.DB.Users.TABLE +
                        Constants.SQL.OPEN_PAREN + Constants.DB.Users.COL_EXTERNAL_USER_ID + Constants.SQL.CLOSE_PAREN +
                        Constants.SQL.VALUES + "(?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, externalUserId);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    long id = rs.getLong(1);
                    return id;
                }
            }
        }
        // Fallback select (in case of race)
        try (PreparedStatement ps = conn.prepareStatement("SELECT " + Constants.DB.Users.COL_ID + " FROM " + Constants.DB.Users.TABLE + " WHERE " + Constants.DB.Users.COL_EXTERNAL_USER_ID + " = ?" + Constants.SQL.LIMIT_1)) {
            ps.setString(1, externalUserId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getLong(1);
            }
        }
        throw new SQLException("Failed to ensure user");
    }

    // --- User ---

    private static List<String> resolveExternalUserIds(Map<String, String> cfg) {
        // Priority: externalUserIds CSV -> single externalUserId -> users count with prefix -> default
        String csv = cfg.get("externalUserIds");
        if (csv != null && !csv.isBlank()) {
            String[] parts = csv.split(",");
            List<String> list = new ArrayList<>();
            for (String p : parts) {
                String s = p.trim();
                if (!s.isEmpty()) list.add(s);
            }
            if (!list.isEmpty()) return list;
        }
        String single = cfg.get("externalUserId");
        if (single != null && !single.isBlank()) {
            return Collections.singletonList(single);
        }
        int users = Integer.parseInt(cfg.getOrDefault("users", "1"));
        List<String> gen = new ArrayList<>(users);
        for (int i = 1; i <= users; i++) gen.add("Ve43CSFGNFgps5eGk3avtSOClgA3");
        return gen;
    }

    private static List<Long> createAccounts(Connection conn, long userId, int total, int brokerCount, String currency, boolean commitPerBatch, boolean verbose) throws SQLException {
        List<Long> ids = new ArrayList<>(total);
        String sql = Constants.SQL.INSERT_INTO + Constants.DB.Accounts.TABLE +
                Constants.SQL.OPEN_PAREN + String.join(Constants.SQL.COMMA_SPACE,
                Constants.DB.Accounts.COL_USER_ID,
                Constants.DB.Accounts.COL_NAME,
                Constants.DB.Accounts.COL_TYPE,
                Constants.DB.Accounts.COL_IS_ACTIVE,
                Constants.DB.Accounts.COL_NEEDS_DAILY_DATA,
                Constants.DB.Accounts.COL_CURRENCY) + Constants.SQL.CLOSE_PAREN +
                Constants.SQL.VALUES + "(?,?,?,?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (int i = 1; i <= total; i++) {
                boolean isBroker = i <= brokerCount;
                String type = isBroker ? Constants.Accounts.TYPE_BROKER : Constants.Accounts.TYPE_SAVINGS;
                String name = (isBroker ? "Broker Acc " : "NonBroker Acc ") + i;
                ps.setLong(1, userId);
                ps.setString(2, name);
                ps.setString(3, type);
                ps.setBoolean(4, true);
                ps.setBoolean(5, isBroker || i % 2 == 0);
                ps.setString(6, currency);
                ps.addBatch();
            }
            ps.executeBatch();
            if (commitPerBatch && !conn.getAutoCommit()) conn.commit();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                while (rs.next()) ids.add(rs.getLong(1));
            }
        }
        if (ids.size() != total) throw new SQLException("Failed to create all accounts");
        return ids;

    }

    // --- Accounts ---

    private static Map<Long, List<Long>> createHoldingsForBrokerAccounts(Connection conn, List<Long> brokerAccountIds, int holdingsPerBroker, boolean commitPerBatch, boolean verbose) throws SQLException {
        Map<Long, List<Long>> accountHoldings = new LinkedHashMap<>();
        String sql = Constants.SQL.INSERT_INTO + Constants.DB.Holdings.TABLE +
                Constants.SQL.OPEN_PAREN + String.join(Constants.SQL.COMMA_SPACE,
                Constants.DB.Holdings.COL_ACCOUNT_ID,
                Constants.DB.Holdings.COL_NAME,
                Constants.DB.Holdings.COL_SYMBOL) + Constants.SQL.CLOSE_PAREN +
                Constants.SQL.VALUES + "(?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (long accountId : brokerAccountIds) {
                List<Long> holdingIds = new ArrayList<>(holdingsPerBroker);
                for (int h = 1; h <= holdingsPerBroker; h++) {
                    String name = "Holding " + h + " (A" + accountId + ")";
                    String symbol = "SYM" + h + "A" + accountId;
                    ps.setLong(1, accountId);
                    ps.setString(2, name);
                    ps.setString(3, symbol);
                    ps.addBatch();
                }
                ps.executeBatch();
                if (commitPerBatch && !conn.getAutoCommit()) conn.commit();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    while (rs.next()) holdingIds.add(rs.getLong(1));
                }
                accountHoldings.put(accountId, holdingIds);
            }
        }
        return accountHoldings;
    }

    // --- Holdings ---

    private static List<Long> createWatchlists(Connection conn, long userId, int count, boolean commitPerBatch, boolean verbose) throws SQLException {
        List<Long> ids = new ArrayList<>(count);
        String sql = Constants.SQL.INSERT_INTO + Constants.DB.Watchlists.TABLE +
                Constants.SQL.OPEN_PAREN + String.join(Constants.SQL.COMMA_SPACE,
                Constants.DB.Watchlists.COL_USER_ID,
                Constants.DB.Watchlists.COL_NAME) + Constants.SQL.CLOSE_PAREN +
                Constants.SQL.VALUES + "(?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (int i = 1; i <= count; i++) {
                ps.setLong(1, userId);
                ps.setString(2, "Watchlist " + i);
                ps.addBatch();
            }
            ps.executeBatch();
            if (commitPerBatch && !conn.getAutoCommit()) conn.commit();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                while (rs.next()) ids.add(rs.getLong(1));
            }
        }
        if (ids.size() != count) throw new SQLException("Failed to create all watchlists");
        return ids;
    }

    // --- Watchlists ---

    private static void attachAccountsToWatchlists(Connection conn, List<Long> watchlistIds, List<Long> accountIds, boolean commitPerBatch, boolean verbose) throws SQLException {
        String sql = Constants.SQL.INSERT_IGNORE_INTO + Constants.DB.WatchlistAccounts.TABLE +
                Constants.SQL.OPEN_PAREN + String.join(Constants.SQL.COMMA_SPACE,
                Constants.DB.WatchlistAccounts.COL_WATCHLIST_ID,
                Constants.DB.WatchlistAccounts.COL_ACCOUNT_ID) + Constants.SQL.CLOSE_PAREN +
                Constants.SQL.VALUES + "(?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (long wId : watchlistIds) {
                for (long aId : accountIds) {
                    ps.setLong(1, wId);
                    ps.setLong(2, aId);
                    ps.addBatch();
                }
                ps.executeBatch();
                if (commitPerBatch && !conn.getAutoCommit()) conn.commit();
            }
        }
    }

    // Returns only those account IDs from the input list that have needs_daily_data = true
    private static List<Long> filterAccountsNeedingDailyData(Connection conn, List<Long> accountIds) throws SQLException {
        if (accountIds == null || accountIds.isEmpty()) return Collections.emptyList();
        StringBuilder sb = new StringBuilder();
        sb.append(Constants.SQL.SELECT).append(Constants.DB.Accounts.COL_ID)
          .append(Constants.SQL.FROM).append(Constants.DB.Accounts.TABLE)
          .append(Constants.SQL.WHERE).append(Constants.DB.Accounts.COL_ID).append(" IN (");
        for (int i = 0; i < accountIds.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append('?');
        }
        sb.append(") AND ").append(Constants.DB.Accounts.COL_NEEDS_DAILY_DATA).append(Constants.SQL.EQ_1);
        List<Long> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sb.toString())) {
            int idx = 1;
            for (Long id : accountIds) ps.setLong(idx++, id);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(rs.getLong(1));
            }
        }
        return result;
    }

    private static void generateHoldingDaily(Connection conn, Map<Long, List<Long>> accountHoldings, LocalDate startDate, LocalDate endDate, String currency, long seed, int batchSize, boolean commitPerBatch, boolean verbose) throws SQLException {
        String sql = Constants.SQL.INSERT_INTO + Constants.DB.AccountHoldingValuesDaily.TABLE +
                Constants.SQL.OPEN_PAREN + String.join(Constants.SQL.COMMA_SPACE,
                Constants.DB.AccountHoldingValuesDaily.COL_ACCOUNT_ID,
                Constants.DB.AccountHoldingValuesDaily.COL_HOLDING_ID,
                Constants.DB.AccountHoldingValuesDaily.COL_DATE,
                Constants.DB.AccountHoldingValuesDaily.COL_CURRENT_DAY_VALUE,
                Constants.DB.AccountHoldingValuesDaily.COL_INVESTED) + Constants.SQL.CLOSE_PAREN +
                Constants.SQL.VALUES + "(?,?,?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int pending = 0;
            for (Map.Entry<Long, List<Long>> e : accountHoldings.entrySet()) {
                long accountId = e.getKey();
                for (long holdingId : e.getValue()) {
                    double value = 1;
                    double invested = 1;
                    for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
                        ps.setLong(1, accountId);
                        ps.setLong(2, holdingId);
                        ps.setDate(3, Date.valueOf(d));
                        ps.setBigDecimal(4, BigDecimal.valueOf(value));
                        ps.setBigDecimal(5, BigDecimal.valueOf(invested));
                        value = value * 1.001;
                        invested = invested * 1.0005; // invested grows slower than value
                        ps.addBatch();
                        pending++;
                        if (pending >= batchSize) {
                            ps.executeBatch();
                            if (commitPerBatch && !conn.getAutoCommit()) conn.commit();
                            pending = 0;
                        }
                    }
                }
            }
            if (pending > 0) {
                ps.executeBatch();
                if (commitPerBatch && !conn.getAutoCommit()) conn.commit();
            }
        }
    }

    private static int totalHoldings(Map<Long, List<Long>> accountHoldings) {
        int c = 0;
        for (List<Long> l : accountHoldings.values()) c += l.size();
        return c;
    }

    // --- Transactions (unified table) ---
    private static void generateSampleTransactions(Connection conn, Map<Long, List<Long>> brokerAccountHoldings, Map<Long, Long> nonBrokerAggHolding, LocalDate endDate, int countPerHolding, int batchSize, boolean commitPerBatch, boolean verbose) throws SQLException {
        String sql = Constants.SQL.INSERT_INTO + Constants.DB.Transactions.TABLE +
                Constants.SQL.OPEN_PAREN + String.join(Constants.SQL.COMMA_SPACE,
                Constants.DB.Transactions.COL_ACCOUNT_ID,
                Constants.DB.Transactions.COL_HOLDING_ID,
                Constants.DB.Transactions.COL_TXN_DATE,
                Constants.DB.Transactions.COL_INVESTED,
                Constants.DB.Transactions.COL_VALUE) + Constants.SQL.CLOSE_PAREN +
                Constants.SQL.VALUES + "(?,?,?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int pending = 0;
            long total = 0;
            // Broker holdings
            for (Map.Entry<Long, List<Long>> e : brokerAccountHoldings.entrySet()) {
                long accountId = e.getKey();
                for (long holdingId : e.getValue()) {
                    for (int i = countPerHolding - 1; i >= 0; i--) {
                        LocalDate d = endDate.minusDays(i);
                        ps.setLong(1, accountId);
                        ps.setLong(2, holdingId);
                        ps.setDate(3, Date.valueOf(d));
                        ps.setBigDecimal(4, BigDecimal.valueOf((countPerHolding - i))); // invested
                        ps.setBigDecimal(5, BigDecimal.valueOf((countPerHolding - i) * 1.1)); // value grows
                        ps.addBatch();
                        pending++;
                        if (pending >= batchSize) {
                            long tBatch = System.nanoTime();
                            ps.executeBatch();
                            if (commitPerBatch && !conn.getAutoCommit()) conn.commit();
                            total += pending;
                            if (verbose)
                                System.out.printf("[transactions] Batch inserted: +%d rows (total=%d) in %s%n", pending, total, fmtMs(tBatch));
                            pending = 0;
                        }
                    }
                }
            }
            // Non-broker aggregate holdings (one per non-broker account)
            for (Map.Entry<Long, Long> e : nonBrokerAggHolding.entrySet()) {
                long accountId = e.getKey();
                long holdingId = e.getValue();
                for (int i = countPerHolding - 1; i >= 0; i--) {
                    LocalDate d = endDate.minusDays(i);
                    ps.setLong(1, accountId);
                    ps.setLong(2, holdingId);
                    ps.setDate(3, Date.valueOf(d));
                    ps.setBigDecimal(4, BigDecimal.valueOf(1));
                    ps.setBigDecimal(5, BigDecimal.valueOf(i + 1));
                    ps.addBatch();
                    pending++;
                    if (pending >= batchSize) {
                        ps.executeBatch();
                        if (commitPerBatch && !conn.getAutoCommit()) conn.commit();
                        total += pending;
                        pending = 0;
                    }
                }
            }
            if (pending > 0) {
                ps.executeBatch();
                if (commitPerBatch && !conn.getAutoCommit()) conn.commit();
            }
        }
    }

    // --- Income tags & sources & incomes ---
    private static List<Long> createIncomeTags(Connection conn, long userId, boolean commitPerBatch, boolean verbose) throws SQLException {
        String sql = Constants.SQL.INSERT_INTO + Constants.DB.IncomeTags.TABLE +
                Constants.SQL.OPEN_PAREN + String.join(Constants.SQL.COMMA_SPACE,
                Constants.DB.IncomeTags.COL_USER_ID,
                Constants.DB.IncomeTags.COL_NAME,
                Constants.DB.IncomeTags.COL_IS_DEFAULT) + Constants.SQL.CLOSE_PAREN +
                Constants.SQL.VALUES + "(?,?,?)";
        List<Long> ids = new ArrayList<>(2);
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            String[] names = {"Salary", "Bonus"};
            for (int i = 0; i < names.length; i++) {
                ps.setLong(1, userId);
                ps.setString(2, names[i]);
                ps.setBoolean(3, i == 0); // first default
                ps.addBatch();
            }
            ps.executeBatch();
            if (commitPerBatch && !conn.getAutoCommit()) conn.commit();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                while (rs.next()) ids.add(rs.getLong(1));
            }
        }
        return ids;
    }

    private static List<Long> createIncomeSources(Connection conn, long userId, boolean commitPerBatch, boolean verbose) throws SQLException {
        String sql = "INSERT INTO " + Constants.DB.IncomeSources.TABLE + " (" + String.join(", ", Constants.DB.IncomeSources.COL_USER_ID, Constants.DB.IncomeSources.COL_NAME) + ") VALUES (?,?)";
        List<Long> ids = new ArrayList<>(2);
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            String[] names = {"Company", "Freelance"};
            for (String name : names) {
                ps.setLong(1, userId);
                ps.setString(2, name);
                ps.addBatch();
            }
            ps.executeBatch();
            if (commitPerBatch && !conn.getAutoCommit()) conn.commit();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                while (rs.next()) ids.add(rs.getLong(1));
            }
        }
        return ids;
    }

    private static void generateSampleIncomes(Connection conn, long userId, List<Long> incomeSourceIds, List<Long> incomeTagIds, String currency, LocalDate endDate, int totalCount, boolean commitPerBatch, boolean verbose) throws SQLException {
        if (incomeSourceIds.isEmpty() || incomeTagIds.isEmpty()) return;
        String sql = "INSERT INTO " + Constants.DB.Incomes.TABLE + " (" + String.join(", ", Constants.DB.Incomes.COL_USER_ID, Constants.DB.Incomes.COL_INCOME_SOURCE_ID, Constants.DB.Incomes.COL_INCOME_TAG_ID, Constants.DB.Incomes.COL_NET_AMOUNT, Constants.DB.Incomes.COL_TAX_PAID, Constants.DB.Incomes.COL_CURRENCY, Constants.DB.Incomes.COL_CREDITED_DATE) + ") VALUES (?,?,?,?,?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int perGroup = totalCount / 2; // 5 per tag/source
            // first pair
            for (int i = 0; i < perGroup; i++) {
                ps.setLong(1, userId);
                ps.setLong(2, incomeSourceIds.get(0));
                ps.setLong(3, incomeTagIds.get(0));
                ps.setBigDecimal(4, new BigDecimal("1000.00").add(new BigDecimal(i)));
                ps.setBigDecimal(5, new BigDecimal("100.00"));
                ps.setString(6, currency);
                ps.setDate(7, Date.valueOf(endDate.minusDays(i)));
                ps.addBatch();
            }
            // second pair
            for (int i = 0; i < perGroup; i++) {
                ps.setLong(1, userId);
                ps.setLong(2, incomeSourceIds.get(1));
                ps.setLong(3, incomeTagIds.get(1));
                ps.setBigDecimal(4, new BigDecimal("2000.00").add(new BigDecimal(i)));
                ps.setBigDecimal(5, new BigDecimal("200.00"));
                ps.setString(6, currency);
                ps.setDate(7, Date.valueOf(endDate.minusDays(perGroup + i)));
                ps.addBatch();
            }
            ps.executeBatch();
            if (commitPerBatch && !conn.getAutoCommit()) conn.commit();
        }
    }

    // --- Args parser ---
    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> map = new HashMap<>();
        if (args == null) return map;
        for (String a : args) {
            if (a == null) continue;
            int i = a.indexOf('=');
            if (i > 0) {
                map.put(a.substring(0, i), a.substring(i + 1));
            }
        }
        return map;
    }

    // --- Aggregate holding for non-broker accounts (to satisfy FK on account_holding_values_daily.holding_id) ---
    private static Map<Long, Long> createAggregateHoldingForAccounts(Connection conn, List<Long> accountIds, boolean commitPerBatch, boolean verbose) throws SQLException {
        Map<Long, Long> map = new LinkedHashMap<>();
        if (accountIds.isEmpty()) return map;
        String sql = "INSERT INTO holdings (account_id, name, symbol) VALUES (?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (long accountId : accountIds) {
                ps.setLong(1, accountId);
                ps.setString(2, Constants.Accounts.AGG_HOLDING_NAME);
                ps.setString(3, Constants.Accounts.AGG_HOLDING_SYMBOL);
                ps.addBatch();
            }
            ps.executeBatch();
            if (commitPerBatch && !conn.getAutoCommit()) conn.commit();
            Iterator<Long> iter = accountIds.iterator();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                while (rs.next() && iter.hasNext()) {
                    map.put(iter.next(), rs.getLong(1));
                }
            }
        }
        if (map.size() != accountIds.size())
            throw new SQLException("Failed to create aggregate holdings for all non-broker accounts");
        return map;
    }

    private static void generateAccountDailyForNonBroker(Connection conn, List<Long> nonBrokerAccountIds, Map<Long, Long> aggHoldingIds, LocalDate startDate, LocalDate endDate, String currency, long seed, int batchSize, boolean commitPerBatch, boolean verbose) throws SQLException {
        if (nonBrokerAccountIds.isEmpty()) return;
        String sql = "INSERT INTO account_holding_values_daily (account_id, holding_id, date, current_day_value) VALUES (?,?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int pending = 0;
            for (long accountId : nonBrokerAccountIds) {
                Long holdingId = aggHoldingIds.get(accountId);
                if (holdingId == null) throw new SQLException("Missing aggregate holding id for account " + accountId);
                double value = 1;
                for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
                    ps.setLong(1, accountId);
                    ps.setLong(2, holdingId);
                    ps.setDate(3, Date.valueOf(d));
                    ps.setBigDecimal(4, BigDecimal.valueOf(value));
                    value = value * 1.001;
                    ps.addBatch();
                    pending++;
                    if (pending >= batchSize) {
                        ps.executeBatch();
                        if (commitPerBatch && !conn.getAutoCommit()) conn.commit();
                        pending = 0;
                    }
                }
            }
            if (pending > 0) {
                ps.executeBatch();
                if (commitPerBatch && !conn.getAutoCommit()) conn.commit();
            }
        }
    }

    private static String fmtMs(long startNs) {
        double ms = (System.nanoTime() - startNs) / 1_000_000.0;
        return String.format(Locale.ROOT, "%.1f ms", ms);
    }

    private static void trySetSessionVar(Connection conn, String name, boolean enable, boolean verbose) {
        try (Statement st = conn.createStatement()) {
            st.execute("SET SESSION " + name + " = " + (enable ? "1" : "0"));
            if (verbose) System.out.printf("Session %s set to %s%n", name, enable ? "1" : "0");
        } catch (SQLException e) {
            if (verbose) System.out.printf("Warning: could not set %s: %s%n", name, e.getMessage());
        }
    }

    // Holds all runtime configuration parsed from CLI args
    private static class Config {
        List<String> externalUserIds;
        int accountsTotal;
        int brokerAccounts;
        int holdingsPerBroker;
        int watchlists;
        int days;
        String currency;
        long seed;
        boolean autoCommit;
        int batchSize;
        boolean disableFks;
        boolean disableUniqueChecks;
        boolean commitPerBatch;
        LocalDate startDate;
        LocalDate endDate;

        static Config fromArgs(String[] args) {
            Map<String, String> cfg = parseArgs(args);
            Config c = new Config();
            c.externalUserIds = resolveExternalUserIds(cfg);
            c.accountsTotal = Integer.parseInt(cfg.getOrDefault("accountsTotal", "10"));
            c.brokerAccounts = Integer.parseInt(cfg.getOrDefault("brokerAccounts", "5"));
            c.holdingsPerBroker = Integer.parseInt(cfg.getOrDefault("holdingsPerBroker", "5"));
            c.watchlists = Integer.parseInt(cfg.getOrDefault("watchlists", "5"));
            c.days = Integer.parseInt(cfg.getOrDefault("days", "3650"));
            c.currency = cfg.getOrDefault("currency", "INR");
            c.seed = Long.parseLong(cfg.getOrDefault("seed", "42"));
            c.autoCommit = Boolean.parseBoolean(cfg.getOrDefault("autoCommit", "false"));
            c.batchSize = Integer.parseInt(cfg.getOrDefault("batchSize", "200000"));
            c.disableFks = Boolean.parseBoolean(cfg.getOrDefault("disableFks", "true"));
            c.disableUniqueChecks = Boolean.parseBoolean(cfg.getOrDefault("disableUniqueChecks", "true"));
            c.commitPerBatch = Boolean.parseBoolean(cfg.getOrDefault("commitPerBatch", "false"));
            c.endDate = cfg.containsKey("endDate") ? LocalDate.parse(cfg.get("endDate")) : LocalDate.now();
            c.startDate = c.endDate.minusDays(c.days - 1L);
            return c;
        }
    }

}
