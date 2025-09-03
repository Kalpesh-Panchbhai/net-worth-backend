package com.networth.networthbackend.tools;

import com.networth.networthbackend.common.Constants;
import com.networth.networthbackend.db.Database;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.*;

/**
 * DailyDataRunner
 * <p>
 * Canonical runner for generating daily time-series data across entities.
 * <p>
 * Phase 1: Non-broker accounts with needs_daily_data = false
 * - Source of truth: sparse rows in `transactions`
 * - Target: `account_holding_values_daily`
 * - Logic: forward-fill (running) value and invested between transaction dates up to today
 * <p>
 * Designed to be invoked via:
 * - CLI main() for ad-hoc/manual runs
 * - AWS Lambda (scheduled) by calling the static run(Map<String,String>) from your handler
 * without requiring AWS SDK types here. Create a small Lambda wrapper that reads
 * event/environment and calls DailyDataRunner.run(params).
 * <p>
 * Params (key=value):
 * batchSize=2000
 * maxAccounts=0            (0 means no limit)
 * <p>
 * Example CLI:
 * java -cp target/net-worth-backend-*-jar-with-dependencies.jar \
 * com.networth.networthbackend.tools.DailyDataRunner batchSize=5000
 */
public class DailyDataRunner {

    // ---- Public entrypoints ----

    public static void main(String[] args) throws Exception {
        Map<String, String> params = parseArgs(args);
        run(params);
    }

    public static void run() throws Exception {
        run(Collections.emptyMap());
    }

    public static void run(Map<String, String> params) throws Exception {
        int batchSize = Integer.parseInt(params.getOrDefault("batchSize", "2000"));
        int maxAccounts = Integer.parseInt(params.getOrDefault("maxAccounts", "0"));
        LocalDate today = LocalDate.now();

        try (Connection conn = Database.getConnection()) {
            conn.setAutoCommit(false);

            // Phase 1: Non-broker accounts (needs_daily_data = 0)
            List<Long> accountIds = findEligibleNonBrokerAccounts(conn, maxAccounts);
            if (!accountIds.isEmpty()) {
                backfillNonBrokerAccounts(conn, accountIds, today, batchSize);
            }
        }
    }

    // ---- Phase 1: Non-broker backfill ----

    private static void backfillNonBrokerAccounts(Connection conn, List<Long> accountIds, LocalDate today, int batchSize) throws SQLException {
        String upsertSql = "INSERT INTO " + Constants.DB.AccountHoldingValuesDaily.TABLE + " (" + String.join(", ", Constants.DB.AccountHoldingValuesDaily.COL_ACCOUNT_ID, Constants.DB.AccountHoldingValuesDaily.COL_HOLDING_ID, Constants.DB.AccountHoldingValuesDaily.COL_DATE, Constants.DB.AccountHoldingValuesDaily.COL_CURRENT_DAY_VALUE, Constants.DB.AccountHoldingValuesDaily.COL_INVESTED) + ") VALUES (?,?,?,?,?) ON DUPLICATE KEY UPDATE " + Constants.DB.AccountHoldingValuesDaily.COL_CURRENT_DAY_VALUE + "=VALUES(" + Constants.DB.AccountHoldingValuesDaily.COL_CURRENT_DAY_VALUE + "), " + Constants.DB.AccountHoldingValuesDaily.COL_INVESTED + "=VALUES(" + Constants.DB.AccountHoldingValuesDaily.COL_INVESTED + ")";

        try (PreparedStatement upsert = conn.prepareStatement(upsertSql)) {
            int pending = 0;

            for (long accountId : accountIds) {
                List<Long> holdingIds = findHoldingsWithTransactions(conn, accountId);
                for (long holdingId : holdingIds) {
                    List<Tx> txs = readTransactions(conn, accountId, holdingId);
                    if (txs.isEmpty()) continue;

                    LocalDate d = txs.get(0).date; // start at first transaction date
                    BigDecimal lastValue = BigDecimal.ZERO;
                    BigDecimal lastInvested = BigDecimal.ZERO;
                    int tIdx = 0;

                    while (!d.isAfter(today)) {
                        // advance tx pointer if txn exists for this day
                        while (tIdx < txs.size() && !txs.get(tIdx).date.isAfter(d)) {
                            lastValue = txs.get(tIdx).value;
                            lastInvested = txs.get(tIdx).invested;
                            tIdx++;
                        }

                        // upsert daily row
                        upsert.setLong(1, accountId);
                        upsert.setLong(2, holdingId);
                        upsert.setDate(3, java.sql.Date.valueOf(d));
                        upsert.setBigDecimal(4, lastValue);
                        upsert.setBigDecimal(5, lastInvested);
                        upsert.addBatch();
                        pending++;

                        if (pending >= batchSize) {
                            upsert.executeBatch();
                            conn.commit();
                            pending = 0;
                        }

                        d = d.plusDays(1);
                    }
                }
            }

            if (pending > 0) {
                upsert.executeBatch();
                conn.commit();
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
}
