package com.networth.networthbackend.common;

import com.networth.networthbackend.fx.FxRates;
import com.networth.networthbackend.fx.FxService;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Converts {@link SummaryMetrics} amounts into the user's preferred display currency and fills in
 * the derived {@code gain} / {@code dayChange} fields.
 * <p>
 * Each amount is translated at the rate of the date it is stamped with: {@code currentDayValue}
 * at today's rate and {@code previousDayValue} at yesterday's. Rates come from {@link FxService},
 * so this makes no outbound network call and needs one small, cached database read for the whole
 * list.
 * <p>
 * {@code invested} is the one amount today's rate cannot answer correctly. It is a cumulative cost
 * basis built up over years, so translating the total at today's rate restates purchases at a rate
 * that was never paid. Callers that can supply the historically translated basis — anything with
 * the account's cash flows to hand — should overwrite it via
 * {@link #applyHistoricalInvested(SummaryMetrics, BigDecimal)}; see that method for why. The
 * today's-rate figure computed here remains the fallback for rows with no flows, where it is the
 * only basis available.
 * <p>
 * When a rate genuinely cannot be resolved, the item keeps its native amounts and is labelled with
 * its <em>native</em> currency. Callers that aggregate must therefore check
 * {@link #convertedToPreferred} before summing, so an unconverted row can never be added into a
 * total labelled with the preferred currency.
 */
@Slf4j
public final class SummaryCurrencyConverter {

    private static final int SCALE = 2;

    /**
     * Converts every item from its own source currency into {@code preferredCurrency}, in place.
     *
     * @param sourceCurrencyOf the item's native currency
     */
    public <T extends SummaryMetrics> void convert(List<T> items, Function<T, String> sourceCurrencyOf, String preferredCurrency) {
        if (items == null || items.isEmpty()) return;
        String preferred = FxService.normalize(preferredCurrency);
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);

        Set<String> sources = new HashSet<>();
        for (T item : items) sources.add(FxService.normalize(sourceCurrencyOf.apply(item)));
        Map<String, FxRates> ratesBySource = FxService.rates(sources, preferred, yesterday, today);

        for (T item : items) {
            String source = FxService.normalize(sourceCurrencyOf.apply(item));
            FxRates rates = ratesBySource.getOrDefault(source, FxRates.unavailable());

            BigDecimal current = rates.convert(item.getCurrentDayValue(), today);
            BigDecimal invested = rates.convert(item.getInvested(), today);
            BigDecimal previous = rates.convert(item.getPreviousDayValue(), yesterday);

            if (current == null || invested == null || previous == null) {
                log.warn("No FX rate {}->{}; leaving amounts in {}", source, preferred, source);
                item.setCurrentDayValue(scale(nz(item.getCurrentDayValue())));
                item.setPreviousDayValue(scale(nz(item.getPreviousDayValue())));
                item.setInvested(scale(nz(item.getInvested())));
                item.setDisplayCurrency(source);
            } else {
                item.setCurrentDayValue(scale(current));
                item.setPreviousDayValue(scale(previous));
                item.setInvested(scale(invested));
                item.setDisplayCurrency(preferred);
            }
            deriveTotals(item);
        }
    }

    /** True when the item's amounts are in {@code preferredCurrency} and safe to aggregate. */
    public static boolean convertedToPreferred(SummaryMetrics item, String preferredCurrency) {
        return FxService.normalize(preferredCurrency).equals(item.getDisplayCurrency());
    }

    /**
     * Replaces {@code invested} with a cost basis translated at the rates in force when the money
     * actually moved, and re-derives {@code gain} from it.
     * <p>
     * {@link #convert} can only translate the stored cumulative basis at today's rate, which for a
     * foreign-currency account reports a cost that was never paid: a holding bought for $1000 when
     * the rate was 70 is restated as if ₹88,000 had been spent rather than ₹70,000. The FX gain the
     * user genuinely earned then disappears from {@code gain}, and — because {@link XirrService}
     * translates each flow at its own date — {@code gain} can come out negative while {@code xirr}
     * from the same money is positive. That is the disagreement this removes.
     * <p>
     * Historical translation is also the market convention (performance reporting, and the INR cost
     * basis a foreign holding is taxed on) and what the chart series already uses, so this is what
     * makes the summary card, the chart and the XIRR agree.
     * <p>
     * A no-op when {@code invested} is null, which is how a caller signals it has no flows for the
     * row: the today's-rate basis is then left in place rather than blanked.
     */
    public static void applyHistoricalInvested(SummaryMetrics item, BigDecimal invested) {
        if (item == null || invested == null) return;
        item.setInvested(scale(invested));
        deriveTotals(item);
    }

    /** Fills {@code gain} and {@code dayChange} from the (already converted) amounts. */
    public static void deriveTotals(SummaryMetrics item) {
        item.setGain(scale(nz(item.getCurrentDayValue()).subtract(nz(item.getInvested()))));
        item.setDayChange(scale(nz(item.getCurrentDayValue()).subtract(nz(item.getPreviousDayValue()))));
    }

    /** Sums the given items into {@code target}, skipping any row not in {@code preferredCurrency}. */
    public static <T extends SummaryMetrics> List<T> aggregateInto(SummaryMetrics target, List<T> items, String preferredCurrency) {
        String preferred = FxService.normalize(preferredCurrency);
        BigDecimal current = BigDecimal.ZERO;
        BigDecimal previous = BigDecimal.ZERO;
        BigDecimal invested = BigDecimal.ZERO;
        List<T> skipped = new ArrayList<>();
        for (T item : items) {
            if (!convertedToPreferred(item, preferred)) {
                skipped.add(item);
                continue;
            }
            current = current.add(nz(item.getCurrentDayValue()));
            previous = previous.add(nz(item.getPreviousDayValue()));
            invested = invested.add(nz(item.getInvested()));
        }
        target.setCurrentDayValue(scale(current));
        target.setPreviousDayValue(scale(previous));
        target.setInvested(scale(invested));
        target.setDisplayCurrency(preferred);
        deriveTotals(target);
        return skipped;
    }

    private static BigDecimal scale(BigDecimal v) {
        return v.setScale(SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
