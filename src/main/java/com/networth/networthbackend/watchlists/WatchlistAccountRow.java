package com.networth.networthbackend.watchlists;

import com.networth.networthbackend.common.SummaryMetrics;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * One (watchlist, member account) row of native-currency amounts, used to convert each account
 * into the display currency before it is summed into a {@link WatchlistSummary}.
 * Extends {@link SummaryMetrics} so it can go through {@code SummaryCurrencyConverter}.
 * <p>
 * Internal to the aggregation; never serialised to a client.
 */
@Data
@NoArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class WatchlistAccountRow extends SummaryMetrics {
    private long watchlistId;
    private long userId;
    private String watchlistName;
    private Long accountId; // null when the watchlist has no linked accounts
    private String currency; // native currency of the member account
    private String type;     // account type, for XIRR eligibility
    private boolean needsDailyData;
}
