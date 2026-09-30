package com.networth.networthbackend.chartdata;

import com.networth.networthbackend.db.Database;
import com.networth.networthbackend.fx.FxRates;
import com.networth.networthbackend.fx.FxService;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Per-holding value history of a watchlist, sampled every {@code stepDays} and converted into the
 * preferred currency with each sample date's FX rate. Powers the client-side "time machine": the
 * whole timeline is sent once, so scrubbing and playback never go back to the server.
 * <p>
 * {@code account_holding_values_daily} only has rows on dates a holding changed, so each holding
 * is forward-filled to every sample date, the same way the watchlist chart fills its series.
 */
@Slf4j
public class TimelineService {

    public static final int DEFAULT_STEP_DAYS = 7;
    private static final int DISPLAY_SCALE = 2;

    public record Timeline(String displayCurrency, List<LocalDate> dates, List<TimelineAccount> accounts) {
    }

    public record TimelineAccount(long id, String name, String type, List<TimelineHolding> holdings) {
    }

    /** {@code value[i]} / {@code invested[i]} are the amounts on {@code Timeline.dates[i]}. */
    public record TimelineHolding(long id, String name, double[] value, double[] invested) {
    }

    private record Row(long accountId, String accountName, String accountType, String currency,
                       long holdingId, String holdingName, LocalDate date, BigDecimal value, BigDecimal invested) {
    }

    public Timeline getWatchlistTimeline(long watchlistId, int stepDays, String preferredCurrency) {
        String preferred = FxService.normalize(preferredCurrency);
        List<Row> rows = loadRows(watchlistId);
        if (rows.isEmpty()) return new Timeline(preferred, List.of(), List.of());

        LocalDate earliest = rows.stream().map(Row::date).min(LocalDate::compareTo).orElseThrow();
        List<LocalDate> dates = sampleDates(earliest, LocalDate.now(), Math.max(1, stepDays));

        Map<Long, List<Row>> byHolding = new LinkedHashMap<>();
        Map<Long, Row> accountInfo = new LinkedHashMap<>();
        for (Row r : rows) {
            byHolding.computeIfAbsent(r.holdingId(), id -> new ArrayList<>()).add(r);
            accountInfo.putIfAbsent(r.accountId(), r);
        }

        Set<String> currencies = new HashSet<>();
        accountInfo.values().forEach(r -> currencies.add(FxService.normalize(r.currency())));
        Map<String, FxRates> ratesBySource = FxService.rates(currencies, preferred, dates.get(0), dates.get(dates.size() - 1));

        Map<Long, List<TimelineHolding>> holdingsByAccount = new LinkedHashMap<>();
        Set<Long> unconvertibleAccounts = new HashSet<>();
        for (List<Row> series : byHolding.values()) {
            Row first = series.get(0);
            if (unconvertibleAccounts.contains(first.accountId())) continue;
            FxRates rates = ratesBySource.getOrDefault(FxService.normalize(first.currency()), FxRates.unavailable());
            if (!rates.isResolvable()) {
                unconvertibleAccounts.add(first.accountId());
                continue;
            }
            double[] value = new double[dates.size()];
            double[] invested = new double[dates.size()];
            fill(series, dates, rates, value, invested);
            holdingsByAccount.computeIfAbsent(first.accountId(), id -> new ArrayList<>())
                    .add(new TimelineHolding(first.holdingId(), first.holdingName(), value, invested));
        }
        if (!unconvertibleAccounts.isEmpty()) {
            log.warn("Watchlist {} timeline: excluded accounts {}, no FX rate to {}", watchlistId, unconvertibleAccounts, preferred);
        }

        List<TimelineAccount> accounts = new ArrayList<>();
        for (Row info : accountInfo.values()) {
            if (unconvertibleAccounts.contains(info.accountId())) continue;
            List<TimelineHolding> holdings = holdingsByAccount.getOrDefault(info.accountId(), List.of());
            accounts.add(new TimelineAccount(info.accountId(), info.accountName(), info.accountType(), holdings));
        }
        return new Timeline(preferred, dates, accounts);
    }

    /**
     * Forward-fills one holding's sparse rows (sorted by date) onto the sample dates, converting as
     * of each date. Before its first row a holding is worth zero, and so is a date the FX history
     * does not reach (left at zero rather than shown as a native amount in the preferred currency).
     */
    private static void fill(List<Row> series, List<LocalDate> dates, FxRates rates, double[] value, double[] invested) {
        int next = 0;
        BigDecimal lastValue = BigDecimal.ZERO;
        BigDecimal lastInvested = BigDecimal.ZERO;
        for (int i = 0; i < dates.size(); i++) {
            LocalDate d = dates.get(i);
            while (next < series.size() && !series.get(next).date().isAfter(d)) {
                lastValue = series.get(next).value();
                lastInvested = series.get(next).invested();
                next++;
            }
            if (lastValue.signum() == 0 && lastInvested.signum() == 0) continue;
            BigDecimal v = rates.convertAsOf(lastValue, d);
            BigDecimal inv = rates.convertAsOf(lastInvested, d);
            if (v == null || inv == null) continue;
            value[i] = v.setScale(DISPLAY_SCALE, RoundingMode.HALF_UP).doubleValue();
            invested[i] = inv.setScale(DISPLAY_SCALE, RoundingMode.HALF_UP).doubleValue();
        }
    }

    /** Every {@code step} days walking back from {@code end}, so the last sample is always today. */
    static List<LocalDate> sampleDates(LocalDate start, LocalDate end, int step) {
        List<LocalDate> dates = new ArrayList<>();
        for (LocalDate d = end; !d.isBefore(start); d = d.minusDays(step)) dates.add(d);
        if (dates.isEmpty() || dates.get(dates.size() - 1).isAfter(start)) dates.add(start);
        Collections.reverse(dates);
        return dates;
    }

    private List<Row> loadRows(long watchlistId) {
        String sql = """
                SELECT ahvd.account_id, a.name AS account_name, a.type, a.currency,
                       ahvd.holding_id, h.name AS holding_name, ahvd.date, ahvd.value, ahvd.invested
                FROM account_holding_values_daily ahvd
                INNER JOIN watchlist_accounts wa ON wa.account_id = ahvd.account_id
                INNER JOIN accounts a ON a.id = ahvd.account_id
                INNER JOIN holdings h ON h.id = ahvd.holding_id
                WHERE wa.watchlist_id = ?
                ORDER BY ahvd.account_id, ahvd.holding_id, ahvd.date ASC
                """;
        List<Row> rows = new ArrayList<>();
        try (var connection = Database.getConnection(); var statement = connection.prepareStatement(sql)) {
            statement.setLong(1, watchlistId);
            try (var rs = statement.executeQuery()) {
                while (rs.next()) {
                    rows.add(new Row(
                            rs.getLong("account_id"), rs.getString("account_name"), rs.getString("type"),
                            rs.getString("currency"), rs.getLong("holding_id"), rs.getString("holding_name"),
                            rs.getDate("date").toLocalDate(),
                            nz(rs.getBigDecimal("value")), nz(rs.getBigDecimal("invested"))));
                }
            }
        } catch (Exception e) {
            log.warn("Error loading watchlist {} timeline rows: {}. Returning empty list.", watchlistId, e.getMessage());
            return new ArrayList<>();
        }
        return rows;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
