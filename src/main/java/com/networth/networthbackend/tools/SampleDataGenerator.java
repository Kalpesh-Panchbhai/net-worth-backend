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
 * verbose=true|false
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
        Map<String, String> cfg = parseArgs(args);
        List<String> externalUserIds = resolveExternalUserIds(cfg);
        int accountsTotal = Integer.parseInt(cfg.getOrDefault("accountsTotal", "10"));
        int brokerAccounts = Integer.parseInt(cfg.getOrDefault("brokerAccounts", "5"));
        int holdingsPerBroker = Integer.parseInt(cfg.getOrDefault("holdingsPerBroker", "5"));
        int watchlists = Integer.parseInt(cfg.getOrDefault("watchlists", "5"));
        int days = Integer.parseInt(cfg.getOrDefault("days", "3650"));
        String currency = cfg.getOrDefault("currency", "INR");
        long seed = Long.parseLong(cfg.getOrDefault("seed", "42"));
        boolean verbose = Boolean.parseBoolean(cfg.getOrDefault("verbose", "true"));
        boolean autoCommit = Boolean.parseBoolean(cfg.getOrDefault("autoCommit", "false"));
        int batchSize = Integer.parseInt(cfg.getOrDefault("batchSize", "200000"));
        boolean disableFks = Boolean.parseBoolean(cfg.getOrDefault("disableFks", "true"));
        boolean disableUniqueChecks = Boolean.parseBoolean(cfg.getOrDefault("disableUniqueChecks", "true"));
        boolean commitPerBatch = Boolean.parseBoolean(cfg.getOrDefault("commitPerBatch", "false"));

        LocalDate endDate = cfg.containsKey("endDate") ? LocalDate.parse(cfg.get("endDate")) : LocalDate.now();
        LocalDate startDate = endDate.minusDays(days - 1L);

        if (brokerAccounts > accountsTotal) {
            throw new IllegalArgumentException("brokerAccounts cannot exceed accountsTotal");
        }

        if (verbose) {
            System.out.println("Program start: " + java.time.ZonedDateTime.now());
            System.out.printf("Users to process: %d -> %s%n", externalUserIds.size(), String.join(",", externalUserIds));
        }
        try (Connection conn = Database.getConnection()) {
            long tAllStart = System.nanoTime();
            boolean initialAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(autoCommit);
            if (verbose) {
                System.out.printf("Connection autoCommit=%s (was %s)\n", autoCommit, initialAutoCommit);
                System.out.println("TIP: Ensure JDBC URL has rewriteBatchedStatements=true for faster batches.");
            }
            try {
                // Session optimizations for bulk load
                if (disableFks) trySetSessionVar(conn, "FOREIGN_KEY_CHECKS", false, verbose);
                if (disableUniqueChecks) trySetSessionVar(conn, "UNIQUE_CHECKS", false, verbose);
                for (int idx = 0; idx < externalUserIds.size(); idx++) {
                    String externalUserId = externalUserIds.get(idx);
                    long tUser = System.nanoTime();
                    long userId = ensureUser(conn, externalUserId, verbose);
                    long t = System.nanoTime();
                    List<Long> accountIds = createAccounts(conn, userId, accountsTotal, brokerAccounts, currency, commitPerBatch, verbose);
                    if (verbose)
                        System.out.printf("[user=%s] Inserted accounts: %d in %s%n", externalUserId, accountIds.size(), fmtMs(t));
                    // Broker accounts are first 'brokerAccounts' entries
                    List<Long> brokerAccountIds = accountIds.subList(0, brokerAccounts);
                    List<Long> nonBrokerAccountIds = accountIds.subList(brokerAccounts, accountIds.size());

                    t = System.nanoTime();
                    Map<Long, List<Long>> accountHoldings = createHoldingsForBrokerAccounts(conn, brokerAccountIds, holdingsPerBroker, commitPerBatch, verbose);
                    if (verbose)
                        System.out.printf("[user=%s] Inserted holdings: %d across %d broker accounts in %s%n", externalUserId, totalHoldings(accountHoldings), brokerAccountIds.size(), fmtMs(t));
                    t = System.nanoTime();
                    Map<Long, Long> nonBrokerAggHolding = createAggregateHoldingForAccounts(conn, nonBrokerAccountIds, commitPerBatch, verbose);
                    if (verbose)
                        System.out.printf("[user=%s] Inserted aggregate holdings for non-broker accounts: %d in %s%n", externalUserId, nonBrokerAggHolding.size(), fmtMs(t));

                    t = System.nanoTime();
                    List<Long> watchlistIds = createWatchlists(conn, userId, watchlists, commitPerBatch, verbose);
                    attachAccountsToWatchlists(conn, watchlistIds, accountIds, commitPerBatch, verbose);
                    if (verbose)
                        System.out.printf("[user=%s] Inserted watchlists: %d and memberships: %d in %s%n", externalUserId, watchlistIds.size(), (watchlistIds.size() * accountIds.size()), fmtMs(t));

                    // Time-series
                    t = System.nanoTime();
                    generateAccountDailyForNonBroker(conn, nonBrokerAccountIds, nonBrokerAggHolding, startDate, endDate, currency, seed + (idx * 1000L), batchSize, commitPerBatch, verbose);
                    long nbDays = daysBetween(startDate, endDate);
                    if (verbose)
                        System.out.printf("[user=%s] Inserted non-broker daily rows (expected): %d x %d = %d in %s%n", externalUserId, nonBrokerAccountIds.size(), nbDays, (nonBrokerAccountIds.size() * nbDays), fmtMs(t));
                    t = System.nanoTime();
                    generateHoldingDaily(conn, accountHoldings, startDate, endDate, currency, seed + 1 + (idx * 1000L), batchSize, commitPerBatch, verbose);
                    long holdings = totalHoldings(accountHoldings);
                    if (verbose)
                        System.out.printf("[user=%s] Inserted holding daily rows (expected): %d x %d = %d in %s%n", externalUserId, holdings, nbDays, (holdings * nbDays), fmtMs(t));

                    // Transactions: create 10 per holding (including aggregate holdings for non-broker accounts)
                    t = System.nanoTime();
                    generateSampleTransactions(conn, accountHoldings, nonBrokerAggHolding, endDate, 10, batchSize, commitPerBatch, verbose);
                    if (verbose)
                        System.out.printf("[user=%s] Inserted sample transactions: %d holdings x %d each in %s%n", externalUserId, (holdings + nonBrokerAggHolding.size()), 10, fmtMs(t));

                    if (verbose) {
                        System.out.printf("[user=%s] Done. userId=%d, accounts=%d (broker=%d, non-broker=%d), watchlists=%d, days=%d, totalTime=%s%n", externalUserId, userId, accountIds.size(), brokerAccountIds.size(), nonBrokerAccountIds.size(), watchlistIds.size(), days, fmtMs(tUser));
                    }
                    conn.commit();
                }
                if (!autoCommit) conn.commit();
            } catch (Exception e) {
                try {
                    if (!autoCommit) conn.rollback();
                } catch (SQLException ignored) {
                }
                throw e;
            } finally {
                // Re-enable session checks if we disabled them
                if (disableUniqueChecks) trySetSessionVar(conn, "UNIQUE_CHECKS", true, verbose);
                if (disableFks) trySetSessionVar(conn, "FOREIGN_KEY_CHECKS", true, verbose);
                try {
                    conn.setAutoCommit(initialAutoCommit);
                } catch (SQLException ignored) {
                }
            }
            if (verbose) System.out.println("Program end: " + java.time.ZonedDateTime.now());
        }
    }

    // --- User ---

    private static long ensureUser(Connection conn, String externalUserId, boolean verbose) throws SQLException {
        // Try select
        try (PreparedStatement ps = conn.prepareStatement("SELECT id FROM users WHERE external_user_id = ? LIMIT 1")) {
            ps.setString(1, externalUserId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    long id = rs.getLong(1);
                    if (verbose) System.out.printf("User exists: id=%d, externalUserId=%s%n", id, externalUserId);
                    return id;
                }
            }
        }
        // Insert
        try (PreparedStatement ps = conn.prepareStatement("INSERT INTO users (external_user_id) VALUES (?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, externalUserId);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    long id = rs.getLong(1);
                    if (verbose) System.out.printf("User created: id=%d, externalUserId=%s%n", id, externalUserId);
                    return id;
                }
            }
        }
        // Fallback select (in case of race)
        try (PreparedStatement ps = conn.prepareStatement("SELECT id FROM users WHERE external_user_id = ? LIMIT 1")) {
            ps.setString(1, externalUserId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getLong(1);
            }
        }
        throw new SQLException("Failed to ensure user");
    }

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
        String prefix = cfg.getOrDefault("userPrefix", "demo-user-");
        List<String> gen = new ArrayList<>(users);
        for (int i = 1; i <= users; i++) gen.add(prefix + i);
        return gen;
    }

    // --- Accounts ---

    private static List<Long> createAccounts(Connection conn, long userId, int total, int brokerCount, String currency, boolean commitPerBatch, boolean verbose) throws SQLException {
        List<Long> ids = new ArrayList<>(total);
        String sql = "INSERT INTO accounts (user_id, name, type, is_active, currency) VALUES (?,?,?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (int i = 1; i <= total; i++) {
                boolean isBroker = i <= brokerCount;
                String type = isBroker ? Constants.Accounts.TYPE_BROKER : Constants.Accounts.TYPE_SAVINGS;
                String name = (isBroker ? "Broker Acc " : "NonBroker Acc ") + i;
                ps.setLong(1, userId);
                ps.setString(2, name);
                ps.setString(3, type);
                ps.setBoolean(4, true);
                ps.setString(5, currency);
                ps.addBatch();
            }
            long tBatch = System.nanoTime();
            ps.executeBatch();
            if (commitPerBatch && !conn.getAutoCommit()) conn.commit();
            if (verbose)
                System.out.printf("[accounts] Batch inserted: +%d rows (total=%d) in %s\n", total, total, fmtMs(tBatch));
            try (ResultSet rs = ps.getGeneratedKeys()) {
                while (rs.next()) ids.add(rs.getLong(1));
            }
        }
        if (ids.size() != total) throw new SQLException("Failed to create all accounts");
        if (verbose)
            System.out.printf("Accounts created: %d (broker=%d, non-broker=%d)%n", total, brokerCount, total - brokerCount);
        return ids;

    }

    // --- Holdings ---

    private static Map<Long, List<Long>> createHoldingsForBrokerAccounts(Connection conn, List<Long> brokerAccountIds, int holdingsPerBroker, boolean commitPerBatch, boolean verbose) throws SQLException {
        Map<Long, List<Long>> accountHoldings = new LinkedHashMap<>();
        String sql = "INSERT INTO holdings (account_id, name, symbol, units) VALUES (?,?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            long totalRows = 0;
            for (long accountId : brokerAccountIds) {
                List<Long> holdingIds = new ArrayList<>(holdingsPerBroker);
                for (int h = 1; h <= holdingsPerBroker; h++) {
                    String name = "Holding " + h + " (A" + accountId + ")";
                    String symbol = "SYM" + h + "A" + accountId;
                    BigDecimal units = new BigDecimal("100.0000");
                    ps.setLong(1, accountId);
                    ps.setString(2, name);
                    ps.setString(3, symbol);
                    ps.setBigDecimal(4, units);
                    ps.addBatch();
                }
                long tBatch = System.nanoTime();
                ps.executeBatch();
                if (commitPerBatch && !conn.getAutoCommit()) conn.commit();
                totalRows += holdingsPerBroker;
                if (verbose)
                    System.out.printf("[holdings] Batch inserted: +%d rows (total=%d) for account %d in %s\n", holdingsPerBroker, totalRows, accountId, fmtMs(tBatch));
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    while (rs.next()) holdingIds.add(rs.getLong(1));
                }
                accountHoldings.put(accountId, holdingIds);
            }
        }
        if (verbose)
            System.out.printf("Holdings created for broker accounts: %d accounts, %d each%n", brokerAccountIds.size(), holdingsPerBroker);
        return accountHoldings;
    }

    // --- Watchlists ---

    private static List<Long> createWatchlists(Connection conn, long userId, int count, boolean commitPerBatch, boolean verbose) throws SQLException {
        List<Long> ids = new ArrayList<>(count);
        String sql = "INSERT INTO watchlists (user_id, name) VALUES (?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (int i = 1; i <= count; i++) {
                ps.setLong(1, userId);
                ps.setString(2, "Watchlist " + i);
                ps.addBatch();
            }
            long tBatch = System.nanoTime();
            ps.executeBatch();
            if (commitPerBatch && !conn.getAutoCommit()) conn.commit();
            if (verbose)
                System.out.printf("[watchlists] Batch inserted: +%d rows (total=%d) in %s\n", count, count, fmtMs(tBatch));
            try (ResultSet rs = ps.getGeneratedKeys()) {
                while (rs.next()) ids.add(rs.getLong(1));
            }
        }
        if (ids.size() != count) throw new SQLException("Failed to create all watchlists");
        if (verbose) System.out.printf("Watchlists created: %d%n", count);
        return ids;
    }

    private static void attachAccountsToWatchlists(Connection conn, List<Long> watchlistIds, List<Long> accountIds, boolean commitPerBatch, boolean verbose) throws SQLException {
        String sql = "INSERT IGNORE INTO watchlist_accounts (watchlist_id, account_id) VALUES (?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            long totalRows = 0;
            for (long wId : watchlistIds) {
                for (long aId : accountIds) {
                    ps.setLong(1, wId);
                    ps.setLong(2, aId);
                    ps.addBatch();
                }
                long tBatch = System.nanoTime();
                ps.executeBatch();
                if (commitPerBatch && !conn.getAutoCommit()) conn.commit();
                totalRows += accountIds.size();
                if (verbose)
                    System.out.printf("[watchlist_accounts] Batch inserted: +%d rows (total=%d) for watchlist %d in %s\n", accountIds.size(), totalRows, wId, fmtMs(tBatch));
            }
        }
        if (verbose)
            System.out.printf("Watchlist memberships created: %d watchlists x %d accounts%n", watchlistIds.size(), accountIds.size());
    }

    private static void generateHoldingDaily(Connection conn, Map<Long, List<Long>> accountHoldings, LocalDate startDate, LocalDate endDate, String currency, long seed, int batchSize, boolean commitPerBatch, boolean verbose) throws SQLException {
        String sql = "INSERT INTO account_holding_values_daily (account_id, holding_id, date, invested, current_day_value) VALUES (?,?,?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int pending = 0;
            long total = 0;
            for (Map.Entry<Long, List<Long>> e : accountHoldings.entrySet()) {
                long accountId = e.getKey();
                for (long holdingId : e.getValue()) {
                    int value = 1;
                    for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
                        ps.setLong(1, accountId);
                        ps.setLong(2, holdingId);
                        ps.setDate(3, Date.valueOf(d));
                        ps.setBigDecimal(4, BigDecimal.valueOf(1));
                        ps.setBigDecimal(5, BigDecimal.valueOf(value));
                        value++;
                        ps.addBatch();
                        pending++;
                        if (pending >= batchSize) {
                            long tBatch = System.nanoTime();
                            ps.executeBatch();
                            if (commitPerBatch && !conn.getAutoCommit()) conn.commit();
                            total += pending;
                            if (verbose)
                                System.out.printf("[ahvd:holdings] Batch inserted: +%d rows (total=%d) in %s%n", pending, total, fmtMs(tBatch));
                            pending = 0;
                        }
                    }
                }
            }
            if (pending > 0) {
                long tBatch = System.nanoTime();
                ps.executeBatch();
                if (commitPerBatch && !conn.getAutoCommit()) conn.commit();
                total += pending;
                if (verbose)
                    System.out.printf("[ahvd:holdings] Batch inserted: +%d rows (total=%d) in %s%n", pending, total, fmtMs(tBatch));
            }
        }
        if (verbose)
            System.out.printf("Holding daily rows inserted: %d holdings x %d days (total rows=%d)%n", totalHoldings(accountHoldings), daysBetween(startDate, endDate), (long) totalHoldings(accountHoldings) * daysBetween(startDate, endDate));
    }

    private static int totalHoldings(Map<Long, List<Long>> accountHoldings) {
        int c = 0;
        for (List<Long> l : accountHoldings.values()) c += l.size();
        return c;
    }

    // --- Transactions (unified table) ---
    private static void generateSampleTransactions(Connection conn, Map<Long, List<Long>> brokerAccountHoldings, Map<Long, Long> nonBrokerAggHolding, LocalDate endDate, int countPerHolding, int batchSize, boolean commitPerBatch, boolean verbose) throws SQLException {
        String sql = "INSERT INTO transactions (account_id, holding_id, txn_date, invested, value) VALUES (?,?,?,?,?)";
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
                        ps.setBigDecimal(4, BigDecimal.valueOf(1)); // invested
                        ps.setBigDecimal(5, BigDecimal.valueOf(i + 1)); // value grows
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
            if (pending > 0) {
                long tBatch = System.nanoTime();
                ps.executeBatch();
                if (commitPerBatch && !conn.getAutoCommit()) conn.commit();
                total += pending;
                if (verbose)
                    System.out.printf("[transactions] Batch inserted: +%d rows (total=%d) in %s%n", pending, total, fmtMs(tBatch));
            }
        }
    }

    private static long daysBetween(LocalDate start, LocalDate end) {
        return java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1;
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
        String sql = "INSERT INTO holdings (account_id, name, symbol, units) VALUES (?,?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (long accountId : accountIds) {
                ps.setLong(1, accountId);
                ps.setString(2, Constants.Accounts.AGG_HOLDING_NAME);
                ps.setString(3, Constants.Accounts.AGG_HOLDING_SYMBOL);
                ps.setBigDecimal(4, new BigDecimal("0.0000"));
                ps.addBatch();
            }
            long tBatch = System.nanoTime();
            ps.executeBatch();
            if (commitPerBatch && !conn.getAutoCommit()) conn.commit();
            if (verbose)
                System.out.printf("[agg_holdings] Batch inserted: +%d rows (total=%d) in %s%n", accountIds.size(), accountIds.size(), fmtMs(tBatch));
            Iterator<Long> iter = accountIds.iterator();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                while (rs.next() && iter.hasNext()) {
                    map.put(iter.next(), rs.getLong(1));
                }
            }
        }
        if (map.size() != accountIds.size())
            throw new SQLException("Failed to create aggregate holdings for all non-broker accounts");
        if (verbose) System.out.printf("Aggregate holdings created for non-broker accounts: %d%n", map.size());
        return map;
    }

    private static void generateAccountDailyForNonBroker(Connection conn, List<Long> nonBrokerAccountIds, Map<Long, Long> aggHoldingIds, LocalDate startDate, LocalDate endDate, String currency, long seed, int batchSize, boolean commitPerBatch, boolean verbose) throws SQLException {
        if (nonBrokerAccountIds.isEmpty()) return;
        String sql = "INSERT INTO account_holding_values_daily (account_id, holding_id, date, invested, current_day_value) VALUES (?,?,?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int pending = 0;
            long total = 0;
            for (long accountId : nonBrokerAccountIds) {
                Long holdingId = aggHoldingIds.get(accountId);
                if (holdingId == null) throw new SQLException("Missing aggregate holding id for account " + accountId);
                int value = 1;
                for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
                    ps.setLong(1, accountId);
                    ps.setLong(2, holdingId);
                    ps.setDate(3, Date.valueOf(d));
                    ps.setBigDecimal(4, BigDecimal.valueOf(1));
                    ps.setBigDecimal(5, BigDecimal.valueOf(value));
                    value++;
                    ps.addBatch();
                    pending++;
                    if (pending >= batchSize) {
                        long tBatch = System.nanoTime();
                        ps.executeBatch();
                        if (commitPerBatch && !conn.getAutoCommit()) conn.commit();
                        total += pending;
                        if (verbose)
                            System.out.printf("[ahvd:nonbroker] Batch inserted: +%d rows (total=%d) in %s\n", pending, total, fmtMs(tBatch));
                        pending = 0;
                    }
                }
            }
            if (pending > 0) {
                long tBatch = System.nanoTime();
                ps.executeBatch();
                if (commitPerBatch && !conn.getAutoCommit()) conn.commit();
                total += pending;
                if (verbose)
                    System.out.printf("[ahvd:nonbroker] Batch inserted: +%d rows (total=%d) in %s\n", pending, total, fmtMs(tBatch));
            }
        }
        if (verbose)
            System.out.printf("Account daily rows inserted (via aggregate holdings): %d accounts x %d days (total rows=%d)%n", nonBrokerAccountIds.size(), daysBetween(startDate, endDate), (long) nonBrokerAccountIds.size() * daysBetween(startDate, endDate));
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

}
