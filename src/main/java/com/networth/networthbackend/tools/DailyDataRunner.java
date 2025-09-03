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

public class DailyDataRunner {

    // ---- Public entrypoints ----

    public static void main(String[] args) throws Exception {
        System.out.println(LocalDateTime.now());
        Map<String, String> params = parseArgs(args);
        run(params);
        System.out.println(LocalDateTime.now());
    }

    private static Map<Long, List<Tx>> readTransactionsByHoldingForAccount(Connection conn, long accountId) throws SQLException {
        String sql = "SELECT " + String.join(", ", Constants.DB.Transactions.COL_HOLDING_ID, Constants.DB.Transactions.COL_TXN_DATE, Constants.DB.Transactions.COL_INVESTED, Constants.DB.Transactions.COL_VALUE) + " FROM " + Constants.DB.Transactions.TABLE + " WHERE " + Constants.DB.Transactions.COL_ACCOUNT_ID + " = ?" + " ORDER BY " + Constants.DB.Transactions.COL_HOLDING_ID + ", " + Constants.DB.Transactions.COL_TXN_DATE + " ASC";
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

    public static void run() throws Exception {
        run(Collections.emptyMap());
    }

    public static void run(Map<String, String> params) throws Exception {
        int batchSize = Integer.parseInt(params.getOrDefault("batchSize", "20000"));
        int maxAccounts = Integer.parseInt(params.getOrDefault("maxAccounts", "0"));
        LocalDate today = LocalDate.now();
        String mode = params.getOrDefault("mode", "all").toLowerCase(Locale.ROOT); // 'all' or '7d'
        Integer windowDays = "all".equals(mode) ? null : 7;

        try (Connection conn = Database.getConnection()) {
            conn.setAutoCommit(false);

            // Phase 1: Non-broker accounts (needs_daily_data = 0)
            List<Long> accountIds = findEligibleNonBrokerAccounts(conn, maxAccounts);
            if (!accountIds.isEmpty()) {
                backfillNonBrokerAccounts(conn, accountIds, today, batchSize, windowDays);
            }

            // Phase 2: Broker accounts (or accounts explicitly marked needs_daily_data = 1)
            List<Long> brokerAccountIds = findEligibleBrokerAccounts(conn, maxAccounts);
            if (!brokerAccountIds.isEmpty()) {
                backfillBrokerAccounts(conn, brokerAccountIds, today, batchSize, windowDays);
            }

            // Single commit at the end for both phases
            conn.commit();
        }
    }

    private static String getAccountCurrency(Connection conn, long accountId) throws SQLException {
        String sql = "SELECT " + Constants.DB.Accounts.COL_CURRENCY + " FROM " + Constants.DB.Accounts.TABLE + " WHERE " + Constants.DB.Accounts.COL_ID + " = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, accountId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString(1);
            }
        }
        return null;
    }

    // ---- Phase 1: Non-broker backfill ----

    private static void backfillNonBrokerAccounts(Connection conn, List<Long> accountIds, LocalDate today, int batchSize, Integer windowDays) throws SQLException {
        try (PreparedStatement upsert = prepareUpsert(conn); PreparedStatement deletePs = prepareDeleteDaily(conn)) {
            int pending = 0;

            for (long accountId : accountIds) {
                Map<Long, List<Tx>> txByHolding = readTransactionsByHoldingForAccount(conn, accountId);
                for (Map.Entry<Long, List<Tx>> e : txByHolding.entrySet()) {
                    long holdingId = e.getKey();
                    List<Tx> txs = e.getValue();
                    if (txs == null || txs.isEmpty()) continue;
                    pending = processHoldingNonBroker(deletePs, upsert, accountId, holdingId, txs, today, batchSize, pending, windowDays);
                }
            }

            if (pending > 0) {
                upsert.executeBatch();
                // defer commit to caller
            }
        }
    }

    private static List<Long> findEligibleNonBrokerAccounts(Connection conn, int limit) throws SQLException {
        String sql = "SELECT a." + Constants.DB.Accounts.COL_ID + " FROM " + Constants.DB.Accounts.TABLE + " a" + " WHERE a." + Constants.DB.Accounts.COL_TYPE + " <> ?" + " AND a." + Constants.DB.Accounts.COL_NEEDS_DAILY_DATA + " = 0" + " AND EXISTS (SELECT 1 FROM " + Constants.DB.Transactions.TABLE + " t WHERE t." + Constants.DB.Transactions.COL_ACCOUNT_ID + " = a." + Constants.DB.Accounts.COL_ID + ")" + (limit > 0 ? " LIMIT " + limit : "");
        List<Long> ids = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, Constants.Accounts.TYPE_BROKER);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) ids.add(rs.getLong(1));
            }
        }
        return ids;
    }

    private static List<Long> findHoldingsWithTransactions(Connection conn, long accountId) throws SQLException {
        String sql = "SELECT DISTINCT " + Constants.DB.Transactions.COL_HOLDING_ID + " FROM " + Constants.DB.Transactions.TABLE + " WHERE " + Constants.DB.Transactions.COL_ACCOUNT_ID + " = ?" + " ORDER BY " + Constants.DB.Transactions.COL_HOLDING_ID;
        List<Long> ids = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, accountId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) ids.add(rs.getLong(1));
            }
        }
        return ids;
    }

    private static List<Tx> readTransactions(Connection conn, long accountId, long holdingId) throws SQLException {
        String sql = "SELECT " + String.join(", ", Constants.DB.Transactions.COL_TXN_DATE, Constants.DB.Transactions.COL_INVESTED, Constants.DB.Transactions.COL_VALUE) + " FROM " + Constants.DB.Transactions.TABLE + " WHERE " + Constants.DB.Transactions.COL_ACCOUNT_ID + " = ?" + " AND " + Constants.DB.Transactions.COL_HOLDING_ID + " = ?" + " ORDER BY " + Constants.DB.Transactions.COL_TXN_DATE + " ASC";
        List<Tx> txs = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, accountId);
            ps.setLong(2, holdingId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    java.sql.Date dt = rs.getDate(1);
                    BigDecimal invested = rs.getBigDecimal(2);
                    BigDecimal value = rs.getBigDecimal(3);
                    txs.add(new Tx(dt.toLocalDate(), invested, value));
                }
            }
        }
        return txs;
    }

    // ---- Phase 2: Broker backfill using Yahoo Finance ----

    private static void backfillBrokerAccounts(Connection conn, List<Long> accountIds, LocalDate today, int batchSize, Integer windowDays) throws Exception {
        YahooFinanceClient yf = new YahooFinanceClient();

        try (PreparedStatement upsert = prepareUpsert(conn); PreparedStatement deletePs = prepareDeleteDaily(conn)) {
            int pending = 0;

            for (long accountId : accountIds) {
                String accountCurrency = getAccountCurrency(conn, accountId);
                // FX cache per account to avoid repeated downloads for the same currency pair and window
                java.util.concurrent.ConcurrentHashMap<String, Map<LocalDate, BigDecimal>> fxCache = new java.util.concurrent.ConcurrentHashMap<>();
                List<Holding> holdings = readHoldings(conn, accountId);
                Map<Long, List<Tx>> txByHolding = readTransactionsByHoldingForAccount(conn, accountId);

                // Start parallel fetches for price maps per holding to overlap network latency
                java.util.concurrent.ExecutorService ex = java.util.concurrent.Executors.newFixedThreadPool(Math.min(Math.max(1, holdings.size()), 8));
                Map<Long, java.util.concurrent.CompletableFuture<Map<LocalDate, BigDecimal>>> priceFutures = new HashMap<>();
                Map<Long, LocalDate> startByHolding = new HashMap<>();
                Map<Long, LocalDate> endByHolding = new HashMap<>();
                // txByHolding already populated above

                for (Holding h : holdings) {
                    List<Tx> txs = txByHolding.get(h.id);
                    if (txs == null || txs.isEmpty()) continue;
                    LocalDate firstTxn = txs.get(0).date;
                    LocalDate end = computeBrokerEndDate(txs, today);
                    if (end.isBefore(firstTxn)) continue;
                    LocalDate start = resolveStartForWindow(firstTxn, end, windowDays);
                    startByHolding.put(h.id, start);
                    endByHolding.put(h.id, end);
                    // already in map

                    final LocalDate sDate = start;
                    final LocalDate eDate = end;
                    final String sym = h.symbol;
                    priceFutures.put(h.id, java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                        try {
                            return getPriceMapInAccountCurrencyCached(yf, fxCache, sym, accountCurrency, sDate, eDate);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }, ex));
                }

                // Assemble and write results sequentially to DB while the fetches complete
                for (Holding h : holdings) {
                    if (!txByHolding.containsKey(h.id)) continue;
                    Map<LocalDate, BigDecimal> priceMap = priceFutures.get(h.id).get();
                    List<Tx> txs = txByHolding.get(h.id);
                    LocalDate start = startByHolding.get(h.id);
                    LocalDate end = endByHolding.get(h.id);
                    // Delete existing and write
                    pending = processHoldingBrokerWithPrepared(deletePs, upsert, accountId, accountCurrency, h, txs, start, end, priceMap, today, batchSize, pending);
                }

                ex.shutdown();
            }

            if (pending > 0) {
                upsert.executeBatch();
                // defer commit to caller
            }
        }
    }

    // ---- Helpers (reusable) ----

    private static PreparedStatement prepareUpsert(Connection conn) throws SQLException {
        String upsertSql = "INSERT INTO " + Constants.DB.AccountHoldingValuesDaily.TABLE + " (" + String.join(", ", Constants.DB.AccountHoldingValuesDaily.COL_ACCOUNT_ID, Constants.DB.AccountHoldingValuesDaily.COL_HOLDING_ID, Constants.DB.AccountHoldingValuesDaily.COL_DATE, Constants.DB.AccountHoldingValuesDaily.COL_CURRENT_DAY_VALUE, Constants.DB.AccountHoldingValuesDaily.COL_INVESTED) + ") VALUES (?,?,?,?,?) ON DUPLICATE KEY UPDATE " + Constants.DB.AccountHoldingValuesDaily.COL_CURRENT_DAY_VALUE + "=VALUES(" + Constants.DB.AccountHoldingValuesDaily.COL_CURRENT_DAY_VALUE + "), " + Constants.DB.AccountHoldingValuesDaily.COL_INVESTED + "=VALUES(" + Constants.DB.AccountHoldingValuesDaily.COL_INVESTED + ")";
        return conn.prepareStatement(upsertSql);
    }

    private static PreparedStatement prepareDeleteDaily(Connection conn) throws SQLException {
        String delSql = "DELETE FROM " + Constants.DB.AccountHoldingValuesDaily.TABLE + " WHERE " + Constants.DB.AccountHoldingValuesDaily.COL_ACCOUNT_ID + " = ?" + " AND " + Constants.DB.AccountHoldingValuesDaily.COL_HOLDING_ID + " = ?" + " AND " + Constants.DB.AccountHoldingValuesDaily.COL_DATE + " BETWEEN ? AND ?";
        return conn.prepareStatement(delSql);
    }

    private static void deleteDailyForHoldingRange(PreparedStatement deletePs, long accountId, long holdingId, LocalDate start, LocalDate end) throws SQLException {
        deletePs.clearParameters();
        deletePs.setLong(1, accountId);
        deletePs.setLong(2, holdingId);
        deletePs.setDate(3, java.sql.Date.valueOf(start));
        deletePs.setDate(4, java.sql.Date.valueOf(end));
        deletePs.executeUpdate();
    }

    private static int processHoldingNonBroker(PreparedStatement deletePs, PreparedStatement upsert, long accountId, long holdingId, List<Tx> txs, LocalDate today, int batchSize, int pending, Integer windowDays) throws SQLException {
        LocalDate firstTxn = txs.get(0).date;
        LocalDate start = resolveStartForWindow(firstTxn, today, windowDays);

        // Delete existing for the window
        deleteDailyForHoldingRange(deletePs, accountId, holdingId, start, today);

        LocalDate d = start; // start at window start or firstTxn
        BigDecimal lastValue = BigDecimal.ZERO;
        BigDecimal lastInvested = BigDecimal.ZERO;
        int tIdx = 0;

        while (!d.isAfter(today)) {
            while (tIdx < txs.size() && !txs.get(tIdx).date.isAfter(d)) {
                lastValue = txs.get(tIdx).value;
                lastInvested = txs.get(tIdx).invested;
                tIdx++;
            }

            upsert.setLong(1, accountId);
            upsert.setLong(2, holdingId);
            upsert.setDate(3, java.sql.Date.valueOf(d));
            upsert.setBigDecimal(4, lastValue);
            upsert.setBigDecimal(5, lastInvested);
            upsert.addBatch();
            pending++;

            if (pending >= batchSize) {
                upsert.executeBatch();
                pending = 0;
            }

            d = d.plusDays(1);
        }
        return pending;
    }

    private static LocalDate resolveStartForWindow(LocalDate firstTxn, LocalDate end, Integer windowDays) {
        if (windowDays == null) return firstTxn; // ALL
        LocalDate start = end.minusDays(windowDays - 1);
        if (start.isBefore(firstTxn)) start = firstTxn;
        return start;
    }

    private static Map<LocalDate, BigDecimal> getPriceMapInAccountCurrencyCached(YahooFinanceClient yf, Map<String, Map<LocalDate, BigDecimal>> fxCache, String symbol, String accountCurrency, LocalDate start, LocalDate end) throws Exception {
        YahooFinanceClient.PriceSeries ps = yf.getDailyCloseWithCurrencyCalendarFilled(symbol, start, end);
        Map<LocalDate, BigDecimal> priceMap = ps.prices();
        String symbolCurrency = ps.currency();
        boolean needsFx = symbolCurrency != null && accountCurrency != null && !symbolCurrency.equalsIgnoreCase(accountCurrency);
        if (!needsFx) return priceMap;

        String fxKey = (symbolCurrency + ":" + accountCurrency + ":" + start + ":" + end).toUpperCase();
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

    private static int processHoldingBrokerWithPrepared(PreparedStatement deletePs, PreparedStatement upsert, long accountId, String accountCurrency, Holding h, List<Tx> txs, LocalDate start, LocalDate end, Map<LocalDate, BigDecimal> priceMap, LocalDate today, int batchSize, int pending) throws Exception {
        // Delete only in the [start, end] window
        deleteDailyForHoldingRange(deletePs, accountId, h.id, start, end);

        Map<LocalDate, UnitsInvested> unitsMap = buildUnitsInvestedCalendar(txs, start, end);

        LocalDate d = start;
        while (!d.isAfter(end)) {
            UnitsInvested ui = unitsMap.get(d);
            BigDecimal units = ui != null ? ui.units : null;
            BigDecimal invested = ui != null ? ui.invested : BigDecimal.ZERO;
            BigDecimal price = priceMap.get(d);

            BigDecimal currVal = BigDecimal.ZERO;
            if (units != null && price != null) currVal = price.multiply(units);

            upsert.setLong(1, accountId);
            upsert.setLong(2, h.id);
            upsert.setDate(3, java.sql.Date.valueOf(d));
            upsert.setBigDecimal(4, currVal);
            upsert.setBigDecimal(5, invested != null ? invested : BigDecimal.ZERO);
            upsert.addBatch();
            pending++;

            if (pending >= batchSize) {
                upsert.executeBatch();
                pending = 0;
            }

            d = d.plusDays(1);
        }
        return pending;
    }

    private static LocalDate computeBrokerEndDate(List<Tx> txs, LocalDate today) {
        LocalDate lastTxnDate = txs.get(txs.size() - 1).date;
        BigDecimal lastUnits = txs.get(txs.size() - 1).value;
        return (lastUnits != null && lastUnits.compareTo(BigDecimal.ZERO) == 0) ? lastTxnDate : today;
    }

    private static Map<LocalDate, BigDecimal> getPriceMapInAccountCurrency(YahooFinanceClient yf, String symbol, String accountCurrency, LocalDate start, LocalDate end) throws Exception {
        YahooFinanceClient.PriceSeries ps = yf.getDailyCloseWithCurrencyCalendarFilled(symbol, start, end);
        Map<LocalDate, BigDecimal> priceMap = ps.prices();
        String symbolCurrency = ps.currency();
        boolean needsFx = symbolCurrency != null && accountCurrency != null && !symbolCurrency.equalsIgnoreCase(accountCurrency);
        if (!needsFx) return priceMap;

        Map<LocalDate, BigDecimal> fxMap = yf.getFxCalendarFilled(symbolCurrency, accountCurrency, start, end);
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
        String sql = "SELECT id, symbol FROM " + Constants.DB.Holdings.TABLE + " WHERE " + Constants.DB.Holdings.COL_ACCOUNT_ID + " = ? ORDER BY id";
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

    private static Map<LocalDate, UnitsInvested> buildUnitsInvestedCalendar(List<Tx> txs, LocalDate start, LocalDate end) {
        Map<LocalDate, UnitsInvested> map = new HashMap<>();
        int tIdx = 0;
        BigDecimal lastUnits = null;
        BigDecimal lastInvested = BigDecimal.ZERO;
        LocalDate d = start;
        while (!d.isAfter(end)) {
            while (tIdx < txs.size() && !txs.get(tIdx).date.isAfter(d)) {
                lastUnits = txs.get(tIdx).value; // value column stores units for broker accounts
                lastInvested = txs.get(tIdx).invested;
                tIdx++;
            }
            if (lastUnits != null) {
                map.put(d, new UnitsInvested(lastUnits, lastInvested));
            }
            d = d.plusDays(1);
        }
        return map;
    }

    private static List<Long> findEligibleBrokerAccounts(Connection conn, int limit) throws SQLException {
        String sql = "SELECT a." + Constants.DB.Accounts.COL_ID + " FROM " + Constants.DB.Accounts.TABLE + " a" + " WHERE a." + Constants.DB.Accounts.COL_TYPE + " = ?" + " AND EXISTS (SELECT 1 FROM " + Constants.DB.Transactions.TABLE + " t WHERE t." + Constants.DB.Transactions.COL_ACCOUNT_ID + " = a." + Constants.DB.Accounts.COL_ID + ")" + (limit > 0 ? " LIMIT " + limit : "");
        List<Long> ids = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, Constants.Accounts.TYPE_BROKER);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) ids.add(rs.getLong(1));
            }
        }
        return ids;
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

    private record Tx(LocalDate date, BigDecimal invested, BigDecimal value) {
    }

    private record Holding(long id, String symbol) {
    }

    private record UnitsInvested(BigDecimal units, BigDecimal invested) {
    }
}
