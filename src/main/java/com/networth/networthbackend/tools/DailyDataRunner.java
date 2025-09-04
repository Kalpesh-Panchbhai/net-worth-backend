package com.networth.networthbackend.tools;

import com.networth.networthbackend.common.Constants;
import com.networth.networthbackend.db.Database;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DailyDataRunner {

    // ---- Common data structures ----

    public static void main(String[] args) throws Exception {
        System.out.println(LocalDateTime.now());
        Map<String, String> params = parseArgs(args);
        run(params);
        System.out.println(LocalDateTime.now());
    }

    public static void run() throws Exception {
        run(Collections.emptyMap());
    }

    public static void run(Map<String, String> params) throws Exception {
        int batchSize = Integer.parseInt(params.getOrDefault(Constants.DailyDataRunner.PARAM_BATCH_SIZE, Constants.DailyDataRunner.DEFAULT_BATCH_SIZE));
        int maxAccounts = Integer.parseInt(params.getOrDefault(Constants.DailyDataRunner.PARAM_MAX_ACCOUNTS, Constants.DailyDataRunner.DEFAULT_MAX_ACCOUNTS));
        LocalDate today = LocalDate.now();
        String mode = params.getOrDefault(Constants.DailyDataRunner.PARAM_MODE, Constants.DailyDataRunner.DEFAULT_MODE).toLowerCase(Locale.ROOT);
        Integer windowDays = Constants.DailyDataRunner.MODE_ALL.equals(mode) ? null : Constants.DailyDataRunner.WINDOW_DAYS_7D;

        try (Connection conn = Database.getConnection()) {
            conn.setAutoCommit(false);

            try (PreparedStatement upsert = prepareUpsert(conn); PreparedStatement deletePs = prepareDeleteDaily(conn)) {

                ProcessingContext context = new ProcessingContext(conn, upsert, deletePs, batchSize, windowDays, today);
                int totalPending = 0;

                // Phase 1: Non-broker accounts (needs_daily_data = 0)
                List<Long> nonBrokerAccountIds = findEligibleNonBrokerAccounts(conn, maxAccounts);
                totalPending = processAccountsBatch(context, nonBrokerAccountIds, false, totalPending);

                // Phase 2: Broker accounts (or accounts explicitly marked needs_daily_data = 1)
                List<Long> brokerAccountIds = findEligibleBrokerAccounts(conn, maxAccounts);
                totalPending = processAccountsBatch(context, brokerAccountIds, true, totalPending);

                // Final batch flush
                if (totalPending > 0) {
                    upsert.executeBatch();
                }
            }

            // Single commit at the end for both phases
            conn.commit();
        }
    }

    // ---- Public entrypoints ----

    private static String getAccountCurrency(Connection conn, long accountId) throws SQLException {
        String sql = Constants.SQL.SELECT + Constants.DB.Accounts.COL_CURRENCY + Constants.SQL.FROM + Constants.DB.Accounts.TABLE + Constants.SQL.WHERE + Constants.DB.Accounts.COL_ID + Constants.SQL.EQ_PARAM;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, accountId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString(1);
            }
        }
        return null;
    }

    private static int processAccountsBatch(ProcessingContext context, List<Long> accountIds, boolean isBroker, int pendingBatches) throws Exception {
        int pending = pendingBatches;

        for (long accountId : accountIds) {
            Map<Long, List<Tx>> txByHolding = readTransactionsByHoldingForAccount(context.conn(), accountId);

            if (isBroker) {
                pending = processBrokerAccount(context, accountId, txByHolding, pending);
            } else {
                pending = processNonBrokerAccount(context, accountId, txByHolding, pending);
            }
        }

        return pending;
    }

    private static int processNonBrokerAccount(ProcessingContext context, long accountId, Map<Long, List<Tx>> txByHolding, int pending) throws SQLException {
        for (Map.Entry<Long, List<Tx>> e : txByHolding.entrySet()) {
            long holdingId = e.getKey();
            List<Tx> txs = e.getValue();
            if (txs == null || txs.isEmpty()) continue;

            DateRange range = computeDateRange(txs, context.today(), context.windowDays(), false);
            pending = processHoldingWithRange(context, accountId, holdingId, txs, range, pending, null);
        }
        return pending;
    }

    private static int processBrokerAccount(ProcessingContext context, long accountId, Map<Long, List<Tx>> txByHolding, int pending) throws Exception {
        String accountCurrency = getAccountCurrency(context.conn(), accountId);
        YahooFinanceClient yf = new YahooFinanceClient();
        ConcurrentHashMap<String, Map<LocalDate, BigDecimal>> fxCache = new ConcurrentHashMap<>();

        List<Holding> holdings = readHoldings(context.conn(), accountId);

        // Parallel price fetching for broker holdings
        ExecutorService ex = Executors.newFixedThreadPool(Math.min(Math.max(Constants.DailyDataRunner.MIN_THREAD_POOL_SIZE, holdings.size()), Constants.DailyDataRunner.DEFAULT_THREAD_POOL_SIZE));
        Map<Long, CompletableFuture<Map<LocalDate, BigDecimal>>> priceFutures = new HashMap<>();
        Map<Long, DateRange> rangeByHolding = new HashMap<>();

        for (Holding h : holdings) {
            List<Tx> txs = txByHolding.get(h.id);
            if (txs == null || txs.isEmpty()) continue;

            DateRange range = computeDateRange(txs, context.today(), context.windowDays(), true);
            rangeByHolding.put(h.id, range);

            priceFutures.put(h.id, CompletableFuture.supplyAsync(() -> {
                try {
                    return getPriceMapInAccountCurrencyCached(yf, fxCache, h.symbol, accountCurrency, range.start(), range.end());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, ex));
        }

        // Process holdings sequentially with fetched prices
        for (Holding h : holdings) {
            if (!txByHolding.containsKey(h.id)) continue;

            Map<LocalDate, BigDecimal> priceMap = priceFutures.get(h.id).get();
            List<Tx> txs = txByHolding.get(h.id);
            DateRange range = rangeByHolding.get(h.id);

            pending = processHoldingWithRange(context, accountId, h.id, txs, range, pending, priceMap);
        }

        ex.shutdown();
        return pending;
    }

    // ---- Unified processing framework ----

    private static Map<Long, List<Tx>> readTransactionsByHoldingForAccount(Connection conn, long accountId) throws SQLException {
        String sql =
                Constants.SQL.SELECT +
                String.join(
                        Constants.SQL.COMMA_SPACE,
                        Constants.DB.Transactions.COL_HOLDING_ID,
                        Constants.DB.Transactions.COL_TXN_DATE,
                        Constants.DB.Transactions.COL_INVESTED,
                        Constants.DB.Transactions.COL_VALUE
                ) +
                Constants.SQL.FROM + Constants.DB.Transactions.TABLE +
                Constants.SQL.WHERE + Constants.DB.Transactions.COL_ACCOUNT_ID + Constants.SQL.EQ_PARAM +
                Constants.SQL.ORDER_BY + Constants.DB.Transactions.COL_HOLDING_ID +
                Constants.SQL.COMMA_SPACE + Constants.DB.Transactions.COL_TXN_DATE + Constants.SQL.ASC;
        Map<Long, List<Tx>> map = new HashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, accountId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long holdingId = rs.getLong(1);
                    java.sql.Date dt = rs.getDate(2);
                    BigDecimal invested = rs.getBigDecimal(3);
                    BigDecimal value = rs.getBigDecimal(4);
                    map.computeIfAbsent(holdingId, k -> new ArrayList<>()).add(new Tx(dt.toLocalDate(), invested, value));
                }
            }
        }
        return map;
    }

    private static DateRange computeDateRange(List<Tx> txs, LocalDate today, Integer windowDays, boolean isBroker) {
        LocalDate firstTxn = txs.get(0).date;
        LocalDate lastTxnDate = txs.get(txs.size() - 1).date;
        BigDecimal lastTxnValue = txs.get(txs.size() - 1).value;

        // Determine end boundary based on account type and last transaction value
        LocalDate endInclusive;
        if (isBroker) {
            // Broker: stop at last txn date if last value (units) is 0, otherwise continue to today
            endInclusive = (lastTxnValue != null && lastTxnValue.compareTo(BigDecimal.ZERO) == 0) ? lastTxnDate : today;
        } else {
            // Non-broker: stop at last txn date if last value is 0, otherwise continue to today
            endInclusive = (lastTxnValue != null && lastTxnValue.compareTo(BigDecimal.ZERO) == 0) ? lastTxnDate : today;
        }

        LocalDate start = resolveStartForWindow(firstTxn, endInclusive, windowDays);
        return new DateRange(start, endInclusive);
    }

    private static LocalDate resolveStartForWindow(LocalDate firstTxn, LocalDate end, Integer windowDays) {
        if (windowDays == null) return firstTxn; // ALL
        LocalDate start = end.minusDays(windowDays - 1);
        if (start.isBefore(firstTxn)) start = firstTxn;
        return start;
    }

    // ---- Common transaction and data access methods ----

    private static List<Long> findEligibleNonBrokerAccounts(Connection conn, int limit) throws SQLException {
        String sql =
                Constants.SQL.SELECT + "a." + Constants.DB.Accounts.COL_ID +
                Constants.SQL.FROM + Constants.DB.Accounts.TABLE + " a" +
                Constants.SQL.WHERE + "a." + Constants.DB.Accounts.COL_TYPE + Constants.SQL.NEQ_PARAM +
                Constants.SQL.AND + "a." + Constants.DB.Accounts.COL_NEEDS_DAILY_DATA + Constants.SQL.EQ_0 +
                Constants.SQL.AND + Constants.SQL.EXISTS_OPEN +
                    Constants.SQL.SELECT + "1" +
                    Constants.SQL.FROM + Constants.DB.Transactions.TABLE + " t" +
                    Constants.SQL.WHERE + "t." + Constants.DB.Transactions.COL_ACCOUNT_ID + " = a." + Constants.DB.Accounts.COL_ID +
                Constants.SQL.CLOSE_PAREN +
                (limit > 0 ? Constants.SQL.LIMIT + limit : "");
        List<Long> ids = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, Constants.Accounts.TYPE_BROKER);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) ids.add(rs.getLong(1));
            }
        }
        return ids;
    }

    private static int processHoldingWithRange(ProcessingContext context, long accountId, long holdingId, List<Tx> txs, DateRange range, int pending, Map<LocalDate, BigDecimal> priceMap) throws SQLException {
        // Delete existing data from start onward
        deleteDailyForHoldingRange(context.deletePs(), accountId, holdingId, range.start());

        LocalDate d = range.start();
        BigDecimal lastValue = BigDecimal.ZERO;
        BigDecimal lastInvested = BigDecimal.ZERO;
        int tIdx = 0;

        while (!d.isAfter(range.end())) {
            // Advance transaction pointer if transaction exists for this day
            while (tIdx < txs.size() && !txs.get(tIdx).date.isAfter(d)) {
                lastValue = txs.get(tIdx).value;
                lastInvested = txs.get(tIdx).invested;
                tIdx++;
            }

            BigDecimal currentDayValue;
            if (priceMap != null) {
                // Broker logic: value is units, multiply by price
                BigDecimal price = priceMap.get(d);
                currentDayValue = (lastValue != null && price != null) ? price.multiply(lastValue) : BigDecimal.ZERO;
            } else {
                // Non-broker logic: value is the current day value directly
                currentDayValue = lastValue;
            }

            // Add to batch
            context.upsert().setLong(1, accountId);
            context.upsert().setLong(2, holdingId);
            context.upsert().setDate(3, java.sql.Date.valueOf(d));
            context.upsert().setBigDecimal(4, currentDayValue);
            context.upsert().setBigDecimal(5, lastInvested != null ? lastInvested : BigDecimal.ZERO);
            context.upsert().addBatch();
            pending++;

            if (pending >= context.batchSize()) {
                context.upsert().executeBatch();
                pending = 0;
            }

            d = d.plusDays(1);
        }

        return pending;
    }

    private static PreparedStatement prepareUpsert(Connection conn) throws SQLException {
        String upsertSql = Constants.SQL.INSERT_INTO + Constants.DB.AccountHoldingValuesDaily.TABLE + Constants.SQL.SPACE + Constants.SQL.OPEN_PAREN + String.join(Constants.SQL.COMMA_SPACE, Constants.DB.AccountHoldingValuesDaily.COL_ACCOUNT_ID, Constants.DB.AccountHoldingValuesDaily.COL_HOLDING_ID, Constants.DB.AccountHoldingValuesDaily.COL_DATE, Constants.DB.AccountHoldingValuesDaily.COL_CURRENT_DAY_VALUE, Constants.DB.AccountHoldingValuesDaily.COL_INVESTED) + Constants.SQL.CLOSE_PAREN + Constants.SQL.VALUES + "(?,?,?,?,?)" + Constants.DailyDataRunner.ON_DUPLICATE_KEY_UPDATE + Constants.DB.AccountHoldingValuesDaily.COL_CURRENT_DAY_VALUE + "=" + String.format(Constants.DailyDataRunner.VALUES_PLACEHOLDER, Constants.DB.AccountHoldingValuesDaily.COL_CURRENT_DAY_VALUE) + Constants.SQL.COMMA_SPACE + Constants.DB.AccountHoldingValuesDaily.COL_INVESTED + "=" + String.format(Constants.DailyDataRunner.VALUES_PLACEHOLDER, Constants.DB.AccountHoldingValuesDaily.COL_INVESTED);
        return conn.prepareStatement(upsertSql);
    }

    private static PreparedStatement prepareDeleteDaily(Connection conn) throws SQLException {
        String delSql =
                Constants.SQL.DELETE_FROM + Constants.DB.AccountHoldingValuesDaily.TABLE +
                Constants.SQL.WHERE + Constants.DB.AccountHoldingValuesDaily.COL_ACCOUNT_ID + Constants.SQL.EQ_PARAM +
                Constants.SQL.AND + Constants.DB.AccountHoldingValuesDaily.COL_HOLDING_ID + Constants.SQL.EQ_PARAM +
                Constants.SQL.AND + Constants.DB.AccountHoldingValuesDaily.COL_DATE + Constants.SQL.GE + Constants.SQL.PARAM;
        return conn.prepareStatement(delSql);
    }

    // ---- Unified holding processing ----

    private static void deleteDailyForHoldingRange(PreparedStatement deletePs, long accountId, long holdingId, LocalDate start) throws SQLException {
        deletePs.clearParameters();
        deletePs.setLong(1, accountId);
        deletePs.setLong(2, holdingId);
        deletePs.setDate(3, java.sql.Date.valueOf(start));
        deletePs.executeUpdate();
    }

    // ---- Helpers (reusable) ----

    private static Map<LocalDate, BigDecimal> getPriceMapInAccountCurrencyCached(YahooFinanceClient yf, Map<String, Map<LocalDate, BigDecimal>> fxCache, String symbol, String accountCurrency, LocalDate start, LocalDate end) throws Exception {
        YahooFinanceClient.PriceSeries ps = yf.getDailyCloseWithCurrencyCalendarFilled(symbol, start, end);
        Map<LocalDate, BigDecimal> priceMap = ps.prices();
        String symbolCurrency = ps.currency();
        boolean needsFx = symbolCurrency != null && accountCurrency != null && !symbolCurrency.equalsIgnoreCase(accountCurrency);
        if (!needsFx) return priceMap;

        String fxKey = String.format(Constants.DailyDataRunner.FX_CACHE_KEY_FORMAT, symbolCurrency, accountCurrency, start, end).toUpperCase();
        Map<LocalDate, BigDecimal> fxMap = fxCache.get(fxKey);
        if (fxMap == null) {
            fxMap = yf.getFxCalendarFilled(symbolCurrency, accountCurrency, start, end);
            fxCache.put(fxKey, fxMap);
        }
        Map<LocalDate, BigDecimal> converted = new HashMap<>();
        LocalDate d = start;
        while (!d.isAfter(end)) {
            BigDecimal p = priceMap.get(d);
            BigDecimal fx = fxMap.get(d);
            if (p != null && fx != null) converted.put(d, p.multiply(fx));
            d = d.plusDays(1);
        }
        return converted;
    }

    private static List<Holding> readHoldings(Connection conn, long accountId) throws SQLException {
        String sql =
                Constants.SQL.SELECT + Constants.DB.Holdings.COL_ID +
                Constants.SQL.COMMA_SPACE + Constants.DB.Holdings.COL_SYMBOL +
                Constants.SQL.FROM + Constants.DB.Holdings.TABLE +
                Constants.SQL.WHERE + Constants.DB.Holdings.COL_ACCOUNT_ID + Constants.SQL.EQ_PARAM +
                Constants.SQL.ORDER_BY + Constants.DB.Holdings.COL_ID;
        List<Holding> res = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, accountId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    res.add(new Holding(rs.getLong(1), rs.getString(2)));
                }
            }
        }
        return res;
    }

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> map = new HashMap<>();
        if (args == null) return map;
        for (String a : args) {
            int i = a.indexOf('=');
            if (i > 0) {
                String k = a.substring(0, i).trim();
                String v = a.substring(i + 1).trim();
                if (!k.isEmpty()) map.put(k, v);
            }
        }
        return map;
    }

    private static List<Long> findEligibleBrokerAccounts(Connection conn, int limit) throws SQLException {
        String sql =
                Constants.SQL.SELECT + "a." + Constants.DB.Accounts.COL_ID +
                Constants.SQL.FROM + Constants.DB.Accounts.TABLE + " a" +
                Constants.SQL.WHERE + "a." + Constants.DB.Accounts.COL_TYPE + Constants.SQL.EQ_PARAM +
                Constants.SQL.AND + Constants.SQL.EXISTS_OPEN +
                    Constants.SQL.SELECT + "1" +
                    Constants.SQL.FROM + Constants.DB.Transactions.TABLE + " t" +
                    Constants.SQL.WHERE + "t." + Constants.DB.Transactions.COL_ACCOUNT_ID + " = a." + Constants.DB.Accounts.COL_ID +
                Constants.SQL.CLOSE_PAREN +
                (limit > 0 ? Constants.SQL.LIMIT + limit : "");
        List<Long> ids = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, Constants.Accounts.TYPE_BROKER);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) ids.add(rs.getLong(1));
            }
        }
        return ids;
    }

    private record ProcessingContext(Connection conn, PreparedStatement upsert, PreparedStatement deletePs,
                                     int batchSize, Integer windowDays, LocalDate today) {
    }

    // ---- Remaining helper methods ----

    private record DateRange(LocalDate start, LocalDate end) {
    }

    // ---- Data structures ----

    private record Tx(LocalDate date, BigDecimal invested, BigDecimal value) {
    }

    private record Holding(long id, String symbol) {
    }
}
