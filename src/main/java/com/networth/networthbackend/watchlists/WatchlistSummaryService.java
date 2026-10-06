package com.networth.networthbackend.watchlists;

import com.networth.networthbackend.common.SummaryCurrencyConverter;
import com.networth.networthbackend.common.Xirr;
import com.networth.networthbackend.common.XirrService;
import com.networth.networthbackend.fx.FxService;
import com.networth.networthbackend.users.UserRepository;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds watchlist summaries by converting each member account's native-currency amounts into the
 * user's preferred display currency <em>before</em> summing them, which is the only correct way to
 * total a multi-currency watchlist.
 * <p>
 * A member whose currency could not be converted is excluded from the total rather than added in
 * raw — a headline net-worth figure must never be the sum of two different currencies. XIRR is
 * pooled across the member accounts' cash flows and returned on the summary, so the client no
 * longer has to walk every watchlist's accounts and transactions to compute it.
 */
@Slf4j
public class WatchlistSummaryService {

    private final WatchlistRepository repository = new WatchlistRepository();
    private final UserRepository userRepository = new UserRepository();
    private final SummaryCurrencyConverter converter = new SummaryCurrencyConverter();
    private final XirrService xirrService = new XirrService();

    public List<WatchlistSummary> summariesByUserId(long userId) throws SQLException {
        return aggregate(repository.listAccountBreakdownByUserId(userId), userRepository.getPreferredCurrency(userId));
    }

    public List<WatchlistSummary> summariesByAccountId(long accountId) throws SQLException {
        List<WatchlistAccountRow> rows = repository.listAccountBreakdownByAccountId(accountId);
        String preferred = rows.isEmpty() ? null : userRepository.getPreferredCurrency(rows.get(0).getUserId());
        return aggregate(rows, preferred);
    }

    private List<WatchlistSummary> aggregate(List<WatchlistAccountRow> rows, String preferredCurrency) {
        String preferred = FxService.normalize(preferredCurrency);
        // Only the rows backed by a real account. The placeholder row an empty watchlist yields has
        // no account and no currency, so converting it would ask for a rate series nothing needs
        // and log a "no FX rate" warning about a row that is never summed into anything.
        converter.convert(rows.stream().filter(r -> r.getAccountId() != null).toList(),
                WatchlistAccountRow::getCurrency, preferred);

        Map<Long, WatchlistSummary> byWatchlist = new LinkedHashMap<>();
        Map<Long, List<WatchlistAccountRow>> membersByWatchlist = new LinkedHashMap<>();
        for (WatchlistAccountRow row : rows) {
            byWatchlist.computeIfAbsent(row.getWatchlistId(), id -> WatchlistSummary.builder()
                    .id(row.getWatchlistId())
                    .userId(row.getUserId())
                    .name(row.getWatchlistName())
                    .build());
            // A watchlist with no linked accounts still yields one placeholder row; it has no
            // member to aggregate, but the watchlist itself must appear in the response.
            if (row.getAccountId() == null) continue;
            membersByWatchlist.computeIfAbsent(row.getWatchlistId(), id -> new ArrayList<>()).add(row);
        }

        Map<Long, String> currencyByAccount = new LinkedHashMap<>();
        Map<Long, BigDecimal> valueByAccount = new LinkedHashMap<>();
        Map<Long, Set<Long>> eligibleAccountsByWatchlist = new LinkedHashMap<>();

        for (Map.Entry<Long, List<WatchlistAccountRow>> entry : membersByWatchlist.entrySet()) {
            for (WatchlistAccountRow member : entry.getValue()) {
                if (!SummaryCurrencyConverter.convertedToPreferred(member, preferred)) continue;
                if (!XirrService.isEligible(member.getType(), member.isNeedsDailyData())) continue;
                currencyByAccount.put(member.getAccountId(), member.getCurrency());
                valueByAccount.put(member.getAccountId(), member.getCurrentDayValue());
                eligibleAccountsByWatchlist
                        .computeIfAbsent(entry.getKey(), id -> new LinkedHashSet<>())
                        .add(member.getAccountId());
            }
        }

        // Load the cash flows before aggregating, not after: each member's cost basis has to be
        // restated from its own flows (translated at the dates the money moved) before the rows are
        // summed, or the watchlist total inherits the today's-rate basis from its members and its
        // gain contradicts the XIRR pooled from those same flows.
        Map<Long, List<Xirr.Flow>> flowsByAccount = currencyByAccount.isEmpty()
                ? Map.of() : xirrService.flowsByAccount(currencyByAccount, preferred);
        for (List<WatchlistAccountRow> members : membersByWatchlist.values()) {
            for (WatchlistAccountRow member : members) {
                SummaryCurrencyConverter.applyHistoricalInvested(
                        member, XirrService.investedFromFlows(flowsByAccount.get(member.getAccountId())));
            }
        }

        for (Map.Entry<Long, WatchlistSummary> entry : byWatchlist.entrySet()) {
            List<WatchlistAccountRow> members = membersByWatchlist.getOrDefault(entry.getKey(), List.of());
            int skipped = SummaryCurrencyConverter.aggregateInto(entry.getValue(), members, preferred).size();
            if (skipped > 0) {
                log.warn("Watchlist {}: excluded {} member account(s) from the total, no FX rate to {}",
                        entry.getKey(), skipped, preferred);
            }
        }

        if (!flowsByAccount.isEmpty()) {
            Map<Long, Double> xirrByWatchlist = xirrService.byGroup(
                    eligibleAccountsByWatchlist, flowsByAccount, valueByAccount);
            for (Map.Entry<Long, WatchlistSummary> entry : byWatchlist.entrySet()) {
                entry.getValue().setXirr(xirrByWatchlist.get(entry.getKey()));
            }
        }
        return new ArrayList<>(byWatchlist.values());
    }
}
