package com.networth.networthbackend.incomes;

import com.networth.networthbackend.tools.YahooFinanceClient;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Enriches incomes with display values converted to the user's preferred currency,
 * using the FX rate as of each income's credited date. Original stored amounts are
 * left untouched; only the {@code converted*} fields are populated.
 */
@Slf4j
public final class IncomeCurrencyConverter {

    private static final String DEFAULT_CURRENCY = "INR";
    private static final int DISPLAY_SCALE = 2;

    private final YahooFinanceClient yahoo;

    public IncomeCurrencyConverter() {
        this(new YahooFinanceClient());
    }

    public IncomeCurrencyConverter(YahooFinanceClient yahoo) {
        this.yahoo = yahoo;
    }

    /**
     * Populates converted* fields on each income in place.
     * If an FX rate cannot be resolved, the converted fields are left null and the
     * frontend falls back to the original amount.
     */
    public void enrich(List<Income> incomes, String preferredCurrency) {
        if (incomes == null || incomes.isEmpty()) return;
        String pref = normalize(preferredCurrency);

        // Group by source currency so we fetch one FX series per (source -> preferred) pair.
        Map<String, List<Income>> byCurrency = new HashMap<>();
        for (Income inc : incomes) {
            String src = normalize(inc.getCurrency());
            byCurrency.computeIfAbsent(src, k -> new ArrayList<>()).add(inc);
        }

        for (Map.Entry<String, List<Income>> entry : byCurrency.entrySet()) {
            String source = entry.getKey();
            List<Income> group = entry.getValue();

            if (source.equalsIgnoreCase(pref)) {
                for (Income inc : group) applyRate(inc, pref, BigDecimal.ONE);
                continue;
            }

            LocalDate min = group.stream().map(Income::getCreditedDate).min(Comparator.naturalOrder()).orElse(null);
            LocalDate max = group.stream().map(Income::getCreditedDate).max(Comparator.naturalOrder()).orElse(null);
            if (min == null || max == null) continue;

            TreeMap<LocalDate, BigDecimal> fx;
            try {
                fx = new TreeMap<>(yahoo.getFxCalendarFilled(source, pref, min, max));
            } catch (Exception e) {
                log.warn("FX fetch failed for {}->{} [{}..{}]: {}", source, pref, min, max, e.getMessage());
                continue; // leave converted fields null
            }

            for (Income inc : group) {
                BigDecimal rate = rateFor(fx, inc.getCreditedDate());
                if (rate != null) applyRate(inc, pref, rate);
            }
        }
    }

    private static void applyRate(Income inc, String pref, BigDecimal rate) {
        inc.setConvertedNetAmount(scale(nz(inc.getNetAmount()).multiply(rate)));
        inc.setConvertedTaxPaid(scale(nz(inc.getTaxPaid()).multiply(rate)));
        inc.setConvertedCurrency(pref);
    }

    private static BigDecimal rateFor(TreeMap<LocalDate, BigDecimal> fx, LocalDate date) {
        if (fx.isEmpty()) return null;
        BigDecimal exact = fx.get(date);
        if (exact != null) return exact;
        Map.Entry<LocalDate, BigDecimal> floor = fx.floorEntry(date);
        if (floor != null) return floor.getValue();
        Map.Entry<LocalDate, BigDecimal> ceil = fx.ceilingEntry(date);
        return ceil != null ? ceil.getValue() : null;
    }

    private static BigDecimal scale(BigDecimal v) {
        return v.setScale(DISPLAY_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static String normalize(String currency) {
        return (currency == null || currency.isBlank()) ? DEFAULT_CURRENCY : currency.trim().toUpperCase();
    }
}
