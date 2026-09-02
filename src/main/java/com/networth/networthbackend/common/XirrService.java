package com.networth.networthbackend.common;

import com.networth.networthbackend.db.Database;
import com.networth.networthbackend.fx.FxRates;
import com.networth.networthbackend.fx.FxService;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Computes XIRR for accounts (and groups of accounts) in the user's display currency.
 * <p>
 * The client used to do this, which meant fetching every eligible account's full transaction list:
 * the watchlists page alone issued {@code 1 + W + (W × A)} requests. All of it now costs one
 * grouped query here, and the answer travels on the summary DTOs.
 * <p>
 * Cash flows are the <em>increments</em> in each holding's cumulative {@code invested}, translated
 * at the FX rate of the date the money actually moved, with the current value as the closing
 * inflow at today's rate. Historical rates are the only correct input to a time-weighted figure:
 * the result is the return the user actually earned measured in their display currency.
 */
@Slf4j
public class XirrService {

    /** One holding's cumulative-invested snapshot on a date, in the account's native currency. */
    private record Snapshot(long accountId, long holdingId, LocalDate date, BigDecimal invested) {
    }

    /**
     * Cash flows per account, in {@code displayCurrency}, for every account in
     * {@code nativeCurrencyByAccount}. Accounts with no transactions are absent from the result.
     */
    public Map<Long, List<Xirr.Flow>> flowsByAccount(Map<Long, String> nativeCurrencyByAccount, String displayCurrency) {
        if (nativeCurrencyByAccount == null || nativeCurrencyByAccount.isEmpty()) return Map.of();
        Snapshots loaded;
        try {
            loaded = loadSnapshots(nativeCurrencyByAccount.keySet());
        } catch (SQLException e) {
            log.warn("XIRR cash-flow load failed for {} accounts: {}", nativeCurrencyByAccount.size(), e.getMessage());
            return Map.of();
        }
        List<Snapshot> snapshots = loaded.rows();
        if (snapshots.isEmpty()) return Map.of();

        LocalDate today = LocalDate.now();
        Map<String, FxRates> fx = FxService.rates(nativeCurrencyByAccount.values(), displayCurrency, loaded.earliest(), today);

        // Snapshots arrive ordered by (holding, date), so a single pass yields the deltas.
        Map<Long, List<Xirr.Flow>> flows = new LinkedHashMap<>();
        Set<Long> incomplete = new HashSet<>();
        long currentHolding = Long.MIN_VALUE;
        BigDecimal previousInvested = BigDecimal.ZERO;
        for (Snapshot s : snapshots) {
            if (s.holdingId() != currentHolding) {
                currentHolding = s.holdingId();
                previousInvested = BigDecimal.ZERO;
            }
            BigDecimal invested = s.invested() == null ? BigDecimal.ZERO : s.invested();
            BigDecimal delta = invested.subtract(previousInvested);
            previousInvested = invested;
            if (delta.signum() == 0) continue;

            FxRates rates = fx.get(FxService.normalize(nativeCurrencyByAccount.get(s.accountId())));
            BigDecimal converted = rates == null ? null : rates.convertAsOf(delta, s.date());
            if (converted == null) {
                // One unconvertible flow makes the whole account's return meaningless: keeping the
                // rest would silently answer with an IRR over an incomplete cash-flow set.
                incomplete.add(s.accountId());
                continue;
            }
            flows.computeIfAbsent(s.accountId(), id -> new ArrayList<>())
                    .add(new Xirr.Flow(s.date(), -converted.doubleValue()));
        }
        if (!incomplete.isEmpty()) {
            log.warn("No XIRR for {} account(s): some cash flows predate the stored FX history", incomplete.size());
            flows.keySet().removeAll(incomplete);
        }
        return flows;
    }

    /**
     * XIRR for each group of accounts (a watchlist, or a whole page). Flows from every member
     * account are pooled, which is the only correct way to aggregate a money-weighted return.
     *
     * @param accountIdsByGroup group key -&gt; member account ids
     */
    public <K> Map<K, Double> byGroup(Map<K, ? extends Collection<Long>> accountIdsByGroup,
                                      Map<Long, String> nativeCurrencyByAccount,
                                      Map<Long, BigDecimal> currentValueByAccount,
                                      String displayCurrency) {
        if (accountIdsByGroup == null || accountIdsByGroup.isEmpty()) return Map.of();
        Map<Long, List<Xirr.Flow>> flows = flowsByAccount(nativeCurrencyByAccount, displayCurrency);
        LocalDate today = LocalDate.now();

        Map<K, Double> out = new HashMap<>();
        for (Map.Entry<K, ? extends Collection<Long>> group : accountIdsByGroup.entrySet()) {
            List<Xirr.Flow> pooled = new ArrayList<>();
            BigDecimal currentValue = BigDecimal.ZERO;
            for (Long accountId : group.getValue()) {
                List<Xirr.Flow> accountFlows = flows.get(accountId);
                if (accountFlows == null || accountFlows.isEmpty()) continue;
                pooled.addAll(accountFlows);
                BigDecimal value = currentValueByAccount.get(accountId);
                if (value != null) currentValue = currentValue.add(value);
            }
            Double rate = closeAndSolve(pooled, currentValue, today);
            if (rate != null) out.put(group.getKey(), rate);
        }
        return out;
    }

    /** XIRR for one already-converted cash-flow list, closing it with today's value. */
    public Double solve(List<Xirr.Flow> flows, BigDecimal currentValue) {
        return closeAndSolve(flows, currentValue, LocalDate.now());
    }

    /** The wire form of a cash-flow list, for clients that need to pool their own groups. */
    public static List<CashFlow> toWire(List<Xirr.Flow> flows) {
        if (flows == null || flows.isEmpty()) return List.of();
        List<CashFlow> out = new ArrayList<>(flows.size());
        for (Xirr.Flow f : flows) {
            out.add(new CashFlow(f.date(), BigDecimal.valueOf(f.amount()).setScale(2, java.math.RoundingMode.HALF_UP)));
        }
        return out;
    }

    /**
     * Cash flows per holding for a single account, in {@code displayCurrency}. One query for the
     * whole account, so a holdings list can report per-holding XIRR without extra round trips.
     */
    public Map<Long, List<Xirr.Flow>> flowsByHolding(long accountId, String nativeCurrency, String displayCurrency) {
        Snapshots loaded;
        try {
            loaded = loadSnapshots(List.of(accountId));
        } catch (SQLException e) {
            log.warn("XIRR cash-flow load failed for account {}: {}", accountId, e.getMessage());
            return Map.of();
        }
        if (loaded.rows().isEmpty()) return Map.of();

        FxRates rates = FxService.rates(nativeCurrency, displayCurrency, loaded.earliest(), LocalDate.now());
        Map<Long, List<Xirr.Flow>> flows = new LinkedHashMap<>();
        Set<Long> incomplete = new HashSet<>();
        long currentHolding = Long.MIN_VALUE;
        BigDecimal previousInvested = BigDecimal.ZERO;
        for (Snapshot s : loaded.rows()) {
            if (s.holdingId() != currentHolding) {
                currentHolding = s.holdingId();
                previousInvested = BigDecimal.ZERO;
            }
            BigDecimal invested = s.invested() == null ? BigDecimal.ZERO : s.invested();
            BigDecimal delta = invested.subtract(previousInvested);
            previousInvested = invested;
            if (delta.signum() == 0) continue;
            BigDecimal converted = rates.convertAsOf(delta, s.date());
            if (converted == null) {
                incomplete.add(s.holdingId());
                continue;
            }
            flows.computeIfAbsent(s.holdingId(), id -> new ArrayList<>())
                    .add(new Xirr.Flow(s.date(), -converted.doubleValue()));
        }
        if (!incomplete.isEmpty()) {
            log.warn("No XIRR for {} holding(s) of account {}: some cash flows predate the stored FX history",
                    incomplete.size(), accountId);
            flows.keySet().removeAll(incomplete);
        }
        return flows;
    }

    private static Double closeAndSolve(List<Xirr.Flow> flows, BigDecimal currentValue, LocalDate today) {
        if (flows == null || flows.isEmpty()) return null;
        List<Xirr.Flow> all = new ArrayList<>(flows);
        if (currentValue != null && currentValue.signum() > 0) {
            all.add(new Xirr.Flow(today, currentValue.doubleValue()));
        }
        return Xirr.of(all);
    }

    /** Snapshots ordered by (holding, date) for the delta pass, plus the earliest date seen. */
    private record Snapshots(List<Snapshot> rows, LocalDate earliest) {
        static final Snapshots EMPTY = new Snapshots(List.of(), LocalDate.EPOCH);
    }

    private Snapshots loadSnapshots(Collection<Long> accountIds) throws SQLException {
        List<Long> ids = accountIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return Snapshots.EMPTY;

        String placeholders = String.join(", ", Collections.nCopies(ids.size(), "?"));
        String sql = Constants.SQL.SELECT
                + String.join(Constants.SQL.COMMA_SPACE,
                Constants.DB.Transactions.COL_ACCOUNT_ID,
                Constants.DB.Transactions.COL_HOLDING_ID,
                Constants.DB.Transactions.COL_TXN_DATE,
                Constants.DB.Transactions.COL_INVESTED)
                + Constants.SQL.FROM + Constants.DB.Transactions.TABLE
                + Constants.SQL.WHERE + Constants.DB.Transactions.COL_ACCOUNT_ID + " IN (" + placeholders + ")"
                + Constants.SQL.ORDER_BY + Constants.DB.Transactions.COL_HOLDING_ID + Constants.SQL.COMMA_SPACE
                + Constants.DB.Transactions.COL_TXN_DATE + Constants.SQL.ASC;

        List<Snapshot> rows = new ArrayList<>();
        LocalDate earliest = null;
        try (Connection conn = Database.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            int i = 1;
            for (Long id : ids) ps.setLong(i++, id);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    LocalDate date = rs.getDate(3).toLocalDate();
                    if (earliest == null || date.isBefore(earliest)) earliest = date;
                    rows.add(new Snapshot(rs.getLong(1), rs.getLong(2), date, rs.getBigDecimal(4)));
                }
            }
        }
        return rows.isEmpty() ? Snapshots.EMPTY : new Snapshots(rows, earliest);
    }

    /** Accounts whose transactions represent invested capital, i.e. those XIRR is meaningful for. */
    public static boolean isEligible(String accountType, boolean needsDailyData) {
        return Constants.Accounts.TYPE_BROKER.equalsIgnoreCase(accountType) || needsDailyData;
    }
}
