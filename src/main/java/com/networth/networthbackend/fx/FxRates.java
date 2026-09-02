package com.networth.networthbackend.fx;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * A date-indexed multiplier converting one currency into another.
 * <p>
 * Multiply an amount denominated in the source currency by {@link #on(LocalDate)} to get the
 * amount in the target currency. Lookups fall back to the nearest earlier date and then the
 * nearest later date, so weekends, market holidays and a not-yet-published close all resolve.
 * <p>
 * A series is never partially usable: {@link #isResolvable()} is true only when every date can be
 * answered, which is what lets callers sum converted amounts across currencies without silently
 * mixing units.
 */
public final class FxRates {

    private static final FxRates IDENTITY = new FxRates(null);
    private static final FxRates UNAVAILABLE = new FxRates(new TreeMap<>());

    private final NavigableMap<LocalDate, BigDecimal> rates; // null => identity (always 1)

    private FxRates(NavigableMap<LocalDate, BigDecimal> rates) {
        this.rates = rates;
    }

    /** Oldest date this series holds a rate for; null for an identity or empty series. */
    public LocalDate earliest() {
        return (rates == null || rates.isEmpty()) ? null : rates.firstKey();
    }

    /** Newest date this series holds a rate for; null for an identity or empty series. */
    public LocalDate latest() {
        return (rates == null || rates.isEmpty()) ? null : rates.lastKey();
    }

    /** Source and target are the same currency: every rate is 1. */
    public static FxRates identity() {
        return IDENTITY;
    }

    /** No rate is known for this pair; {@link #on} returns {@code null}. */
    public static FxRates unavailable() {
        return UNAVAILABLE;
    }

    public static FxRates of(NavigableMap<LocalDate, BigDecimal> rates) {
        if (rates == null || rates.isEmpty()) return UNAVAILABLE;
        return new FxRates(rates);
    }

    /** True when {@link #on} answers every date, so converted amounts are safe to add together. */
    public boolean isResolvable() {
        return rates == null || !rates.isEmpty();
    }

    /**
     * True when the series has a rate at or before {@code date} — i.e. {@link #on} can answer it
     * from history rather than by reaching forward to the earliest rate it happens to hold.
     * <p>
     * Callers converting historical amounts should check this: a series loaded for a recent window
     * will still return <em>something</em> for a date years earlier, and that something is the
     * oldest rate in the window, not the rate that was in force.
     */
    public boolean covers(LocalDate date) {
        if (rates == null) return true;
        return date != null && !rates.isEmpty() && rates.floorKey(date) != null;
    }

    public boolean isIdentity() {
        return rates == null;
    }

    /** Rate for {@code date}: exact, else nearest earlier, else nearest later; null if unknown. */
    public BigDecimal on(LocalDate date) {
        if (rates == null) return BigDecimal.ONE;
        if (rates.isEmpty() || date == null) return null;
        BigDecimal exact = rates.get(date);
        if (exact != null) return exact;
        Map.Entry<LocalDate, BigDecimal> floor = rates.floorEntry(date);
        if (floor != null) return floor.getValue();
        Map.Entry<LocalDate, BigDecimal> ceiling = rates.ceilingEntry(date);
        return ceiling == null ? null : ceiling.getValue();
    }

    /** Converts {@code amount} (null treated as zero); returns null when the rate is unknown. */
    public BigDecimal convert(BigDecimal amount, LocalDate date) {
        BigDecimal rate = on(date);
        if (rate == null) return null;
        BigDecimal value = amount == null ? BigDecimal.ZERO : amount;
        return rate.compareTo(BigDecimal.ONE) == 0 ? value : value.multiply(rate);
    }

    /**
     * Converts a <em>historical</em> amount, refusing dates the series has no rate at or before.
     * <p>
     * {@link #convert} would answer such a date by reaching forward to the oldest rate it holds,
     * which for an amount from years earlier is simply the wrong rate presented as the right one.
     * Use this wherever the date is in the past — transactions, chart points — so the caller can
     * label the row honestly instead.
     */
    public BigDecimal convertAsOf(BigDecimal amount, LocalDate date) {
        return covers(date) ? convert(amount, date) : null;
    }
}
