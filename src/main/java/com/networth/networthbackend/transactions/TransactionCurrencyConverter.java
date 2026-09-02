package com.networth.networthbackend.transactions;

import com.networth.networthbackend.fx.FxRates;
import com.networth.networthbackend.fx.FxService;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts transaction amounts into the user's preferred display currency.
 * <p>
 * The stored columns are <em>cumulative</em> per holding: each row already contains the previous
 * row's total. Converting those cumulative figures directly would be wrong for the thing the UI
 * actually shows — the per-transaction change — because differencing two cumulative amounts each
 * translated at its own date leaves the FX drift between those two dates inside the difference.
 * <p>
 * So each row's <em>increment</em> is converted at that row's own date (the day the money moved),
 * and the cumulative field is the running sum of those converted increments. That makes the list
 * internally consistent: the newest row's cumulative amount equals the sum of the increments
 * above it, and {@code investedDelta} needs no client-side subtraction.
 * <p>
 * Note this is a different quantity from the summary endpoints' {@code invested}, which restates
 * the same cost basis at <em>today's</em> rate. Both are legitimate; they answer different
 * questions ("what I paid, when I paid it" vs "what that basis is worth today"), and each is
 * self-consistent within its own response.
 * <p>
 * Because the cumulative field is a running sum, a holding converts as a whole or not at all: if
 * any of its rows predates the stored FX history the entire holding is left in the account's own
 * currency and labelled as such. Converting only the datable rows would restart the running sum
 * after the others and understate the cost basis on every row that followed.
 * <p>
 * {@code value} is money only for accounts whose transactions record a balance; for broker
 * accounts it holds units and is left untouched.
 */
@Slf4j
public final class TransactionCurrencyConverter {

    private static final int MONEY_SCALE = 2;

    /**
     * @param valueIsUnits when true, {@code value} holds units: only {@code invested} is converted.
     */
    public void convert(List<Transaction> txns, String sourceCurrency, String preferredCurrency, boolean valueIsUnits) {
        if (txns == null || txns.isEmpty()) return;
        String preferred = FxService.normalize(preferredCurrency);
        String source = FxService.normalize(sourceCurrency);

        LocalDate min = null;
        LocalDate max = null;
        for (Transaction t : txns) {
            t.setValueInUnits(valueIsUnits);
            LocalDate d = t.getTxnDate();
            if (d == null) continue;
            if (min == null || d.isBefore(min)) min = d;
            if (max == null || d.isAfter(max)) max = d;
        }
        if (min == null) return;

        FxRates rates = FxService.rates(source, preferred, min, max);

        // Group by holding and walk each group oldest-first so the increments come out right.
        // The list's own order is untouched — rows are mutated in place.
        Map<Long, List<Transaction>> byHolding = new LinkedHashMap<>();
        for (Transaction t : txns) {
            byHolding.computeIfAbsent(t.getHoldingId(), id -> new ArrayList<>()).add(t);
        }

        boolean anyUnconverted = false;
        for (List<Transaction> group : byHolding.values()) {
            group.sort(Comparator.comparing(Transaction::getTxnDate, Comparator.nullsFirst(Comparator.naturalOrder())));
            // A holding converts as a whole or not at all. Converting only the rows the series can
            // date would restart the running total at zero after the ones it cannot, so every later
            // row's cumulative amount would silently omit those increments — a cost basis that is
            // too low, wearing the preferred currency's label as if it were right.
            if (coversEveryDate(group, rates)) {
                applyConverted(group, rates, preferred, valueIsUnits);
            } else {
                anyUnconverted = true;
                applyNative(group, source, valueIsUnits);
            }
        }
        if (anyUnconverted) {
            log.warn("Some holdings' transactions left in {}; their dates predate the stored FX history for {}",
                    source, preferred);
        }
    }

    /**
     * Whether every row in the group can be dated against {@code rates}.
     * <p>
     * This is an exact precondition for {@link FxRates#convertAsOf}, which refuses precisely the
     * dates {@link FxRates#covers} rejects, so checking up front costs nothing and lets the group
     * be converted without a half-applied result to unwind.
     */
    private static boolean coversEveryDate(List<Transaction> group, FxRates rates) {
        for (Transaction t : group) {
            if (!rates.covers(t.getTxnDate())) return false;
        }
        return true;
    }

    /** Converts each row's increment at its own date; cumulative fields become the running sum. */
    private static void applyConverted(List<Transaction> group, FxRates rates, String preferred, boolean valueIsUnits) {
        BigDecimal previousInvested = BigDecimal.ZERO;
        BigDecimal previousValue = BigDecimal.ZERO;
        BigDecimal runningInvested = BigDecimal.ZERO;
        BigDecimal runningValue = BigDecimal.ZERO;

        for (Transaction t : group) {
            BigDecimal nativeInvested = nz(t.getInvested());
            BigDecimal nativeValue = nz(t.getValue());
            BigDecimal investedDelta = nativeInvested.subtract(previousInvested);
            BigDecimal valueDelta = nativeValue.subtract(previousValue);
            previousInvested = nativeInvested;
            previousValue = nativeValue;

            BigDecimal convertedInvestedDelta = rates.convertAsOf(investedDelta, t.getTxnDate());
            BigDecimal convertedValueDelta = valueIsUnits ? valueDelta : rates.convertAsOf(valueDelta, t.getTxnDate());
            runningInvested = runningInvested.add(convertedInvestedDelta);
            runningValue = runningValue.add(convertedValueDelta);

            t.setInvested(scale(runningInvested));
            t.setInvestedDelta(scale(convertedInvestedDelta));
            t.setValue(valueIsUnits ? nativeValue : scale(runningValue));
            t.setValueDelta(valueIsUnits ? valueDelta : scale(convertedValueDelta));
            t.setDisplayCurrency(preferred);
        }
    }

    /**
     * Leaves the stored cumulative amounts as they are, labels them with the account's own
     * currency, and fills the deltas from the native increments so the wire contract still holds.
     */
    private static void applyNative(List<Transaction> group, String source, boolean valueIsUnits) {
        BigDecimal previousInvested = BigDecimal.ZERO;
        BigDecimal previousValue = BigDecimal.ZERO;
        for (Transaction t : group) {
            BigDecimal nativeInvested = nz(t.getInvested());
            BigDecimal nativeValue = nz(t.getValue());
            BigDecimal valueDelta = nativeValue.subtract(previousValue);
            t.setInvestedDelta(scale(nativeInvested.subtract(previousInvested)));
            t.setValueDelta(valueIsUnits ? valueDelta : scale(valueDelta));
            t.setDisplayCurrency(source);
            previousInvested = nativeInvested;
            previousValue = nativeValue;
        }
    }

    /**
     * Labels a single freshly-written transaction without converting it. Write endpoints echo back
     * what was stored, which is in the account's native currency; saying so explicitly stops a
     * client from round-tripping a display-currency figure into the stored cost basis.
     */
    public void labelNative(Transaction txn, String sourceCurrency, boolean valueIsUnits) {
        if (txn == null) return;
        txn.setDisplayCurrency(FxService.normalize(sourceCurrency));
        txn.setValueInUnits(valueIsUnits);
    }

    private static BigDecimal scale(BigDecimal v) {
        return v.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
