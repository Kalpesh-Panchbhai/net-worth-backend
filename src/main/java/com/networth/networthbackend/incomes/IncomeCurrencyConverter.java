package com.networth.networthbackend.incomes;

import com.networth.networthbackend.fx.FxRates;
import com.networth.networthbackend.fx.FxService;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Fills each income's {@code converted*} fields in the user's preferred currency, using the FX
 * rate on its credited date. Stored amounts are left untouched.
 * <p>
 * {@code convertedCurrency} is always populated — with the preferred currency on success, or the
 * income's own currency when no rate exists — so a client can total the converted fields without
 * ever having to fall back to a native amount and mislabel it.
 */
@Slf4j
public final class IncomeCurrencyConverter {

    private static final int DISPLAY_SCALE = 2;

    /** Populates the converted fields on every income in place. */
    public void enrich(List<Income> incomes, String preferredCurrency) {
        if (incomes == null || incomes.isEmpty()) return;
        String preferred = FxService.normalize(preferredCurrency);

        Set<String> sources = new HashSet<>();
        LocalDate min = null;
        LocalDate max = null;
        for (Income inc : incomes) {
            sources.add(FxService.normalize(inc.getCurrency()));
            LocalDate d = inc.getCreditedDate();
            if (d == null) continue;
            if (min == null || d.isBefore(min)) min = d;
            if (max == null || d.isAfter(max)) max = d;
        }
        if (min == null) min = LocalDate.now();
        if (max == null) max = LocalDate.now();

        Map<String, FxRates> ratesBySource = FxService.rates(sources, preferred, min, max);
        Map<String, Integer> unconverted = new HashMap<>();

        for (Income inc : incomes) {
            String source = FxService.normalize(inc.getCurrency());
            FxRates rates = ratesBySource.getOrDefault(source, FxRates.unavailable());
            LocalDate date = inc.getCreditedDate() == null ? LocalDate.now() : inc.getCreditedDate();

            BigDecimal net = rates.convertAsOf(inc.getNetAmount(), date);
            BigDecimal tax = rates.convertAsOf(inc.getTaxPaid(), date);
            if (net == null || tax == null) {
                unconverted.merge(source, 1, Integer::sum);
                apply(inc, nz(inc.getNetAmount()), nz(inc.getTaxPaid()), source);
            } else {
                apply(inc, net, tax, preferred);
            }
        }
        unconverted.forEach((source, count) ->
                log.warn("{} income row(s) left in {}; no FX rate to {}", count, source, preferred));
    }

    /** Enriches a single income, e.g. the row echoed back by a create or update. */
    public void enrich(Income income, String preferredCurrency) {
        if (income != null) enrich(List.of(income), preferredCurrency);
    }

    private static void apply(Income inc, BigDecimal net, BigDecimal tax, String currency) {
        inc.setConvertedNetAmount(scale(net));
        inc.setConvertedTaxPaid(scale(tax));
        inc.setConvertedGrossAmount(scale(net.add(tax)));
        inc.setConvertedCurrency(currency);
    }

    private static BigDecimal scale(BigDecimal v) {
        return v.setScale(DISPLAY_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
