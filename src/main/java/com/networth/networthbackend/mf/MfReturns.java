package com.networth.networthbackend.mf;

import lombok.experimental.UtilityClass;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.NavigableMap;

/**
 * Return and risk maths over one fund's NAV series.
 * <p>
 * Pure functions on a date-to-NAV map, so they are equally usable from the nightly metrics job and
 * from a request path. Everything works in {@code double}: these are ratios feeding a percentage
 * shown to two decimals, not money, so {@code BigDecimal}'s exactness buys nothing and costs a
 * large constant factor in a loop that runs millions of times.
 * <p>
 * The series must be ordered by date; every lookup floors to the newest NAV at or before the date
 * asked for, because NAVs are only struck on business days and a target date computed by calendar
 * arithmetic routinely lands on a weekend or a market holiday.
 */
@UtilityClass
public class MfReturns {

    /** Trading days per year, for annualising a daily standard deviation. */
    private static final double TRADING_DAYS = 252.0;

    private static final double DAYS_PER_YEAR = 365.25;

    /**
     * A NAV this far before the target date is treated as absent rather than floored onto.
     * <p>
     * Without a bound, a fund that stopped reporting years ago would answer a "5 years ago" lookup
     * with whatever its final NAV was, producing a confident and completely wrong return.
     */
    private static final int MAX_FLOOR_DAYS = 15;

    /** Newest NAV at or before {@code date}, or null if none within {@link #MAX_FLOOR_DAYS}. */
    public static Double navOn(NavigableMap<LocalDate, Double> series, LocalDate date) {
        Map.Entry<LocalDate, Double> entry = series.floorEntry(date);
        if (entry == null) return null;
        if (ChronoUnit.DAYS.between(entry.getKey(), date) > MAX_FLOOR_DAYS) return null;
        return entry.getValue();
    }

    /**
     * Compound annual growth rate between two dates, as a fraction (0.12 = 12% a year).
     * <p>
     * Returns null when either end has no usable NAV, or when the window is under a month — a CAGR
     * annualised from a few days' move is a meaningless number with a plausible-looking magnitude.
     */
    public static Double cagr(NavigableMap<LocalDate, Double> series, LocalDate from, LocalDate to) {
        Double start = navOn(series, from);
        Double end = navOn(series, to);
        if (start == null || end == null || start <= 0) return null;

        double days = ChronoUnit.DAYS.between(from, to);
        if (days < 30) return null;
        return Math.pow(end / start, DAYS_PER_YEAR / days) - 1.0;
    }

    /** Trailing CAGR over the last {@code years}, ending at the series' last NAV. */
    public static Double trailingCagr(NavigableMap<LocalDate, Double> series, int years) {
        if (series.isEmpty()) return null;
        LocalDate end = series.lastKey();
        return cagr(series, end.minusYears(years), end);
    }

    /** CAGR from the first NAV to the last. */
    public static Double sinceInceptionCagr(NavigableMap<LocalDate, Double> series) {
        if (series.size() < 2) return null;
        return cagr(series, series.firstKey(), series.lastKey());
    }

    /** Simple (non-annualised) return between two dates, as a fraction. */
    public static Double absoluteReturn(NavigableMap<LocalDate, Double> series, LocalDate from, LocalDate to) {
        Double start = navOn(series, from);
        Double end = navOn(series, to);
        if (start == null || end == null || start <= 0) return null;
        return end / start - 1.0;
    }

    /**
     * Distribution of every {@code windowYears}-long CAGR available in the series, one window per
     * step of {@code stepDays}.
     * <p>
     * This is the honest answer to "what did this fund return?". A single point-to-point CAGR is an
     * artefact of its two endpoints — pick a start at the bottom of a crash and any fund looks
     * excellent. The rolling distribution shows what an investor arriving on an arbitrary day
     * actually got, which is why {@link #worst()} and {@link #shareAbove(double)} are usually more
     * decision-useful than the average.
     *
     * @return null when the series is too short to contain a single full window
     */
    public static Rolling rolling(NavigableMap<LocalDate, Double> series, int windowYears, int stepDays) {
        if (series.size() < 2) return null;
        LocalDate first = series.firstKey();
        LocalDate last = series.lastKey();
        LocalDate windowStart = first;
        if (windowStart.plusYears(windowYears).isAfter(last)) return null;

        int count = 0;
        int negative = 0;
        double sum = 0;
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        // Counted here rather than returned as a list: a 5-year window stepped daily over a 13-year
        // series is ~2,000 values per fund, and the caller only ever wants the summary.
        int above12 = 0;

        while (!windowStart.plusYears(windowYears).isAfter(last)) {
            LocalDate windowEnd = windowStart.plusYears(windowYears);
            Double r = cagr(series, windowStart, windowEnd);
            if (r != null) {
                count++;
                sum += r;
                if (r < min) min = r;
                if (r > max) max = r;
                if (r < 0) negative++;
                if (r >= 0.12) above12++;
            }
            windowStart = windowStart.plusDays(stepDays);
        }
        if (count == 0) return null;
        return new Rolling(count, sum / count, min, max,
                (double) negative / count, (double) above12 / count);
    }

    /**
     * Summary of a rolling-return distribution. Rates are fractions, shares are 0..1.
     *
     * @param worst the worst outcome any investor in this fund actually experienced over the window
     */
    public record Rolling(int windows, double average, double worst, double best,
                          double shareNegative, double shareAbove12) {
        /** Fraction of windows that beat {@code threshold}. Recomputed only for the 12% preset. */
        public double shareAbove(double threshold) {
            return threshold == 0.12 ? shareAbove12 : Double.NaN;
        }
    }

    /**
     * Annualised standard deviation of daily log returns over the trailing {@code years}.
     * <p>
     * Log returns rather than simple ones so the measure is symmetric in direction and additive
     * across time, which is what annualising by root-time assumes.
     */
    public static Double volatility(NavigableMap<LocalDate, Double> series, int years) {
        if (series.isEmpty()) return null;
        LocalDate from = series.lastKey().minusYears(years);
        NavigableMap<LocalDate, Double> window = series.tailMap(from, true);
        if (window.size() < 30) return null;

        double previous = -1;
        double sum = 0;
        double sumSquares = 0;
        int n = 0;
        for (double nav : window.values()) {
            if (previous > 0 && nav > 0) {
                double r = Math.log(nav / previous);
                sum += r;
                sumSquares += r * r;
                n++;
            }
            previous = nav;
        }
        if (n < 30) return null;
        double mean = sum / n;
        // Sample variance (n-1): these are a sample of the fund's return process, not the whole of it.
        double variance = (sumSquares - n * mean * mean) / (n - 1);
        if (variance <= 0) return null;
        return Math.sqrt(variance) * Math.sqrt(TRADING_DAYS);
    }

    /**
     * Largest peak-to-trough fall over the whole series, as a negative fraction.
     * <p>
     * The number that answers "how bad did holding this get?", which volatility alone hides: two
     * funds with equal vol can have very different worst experiences.
     */
    public static Double maxDrawdown(NavigableMap<LocalDate, Double> series) {
        if (series.size() < 2) return null;
        double peak = -1;
        double worst = 0;
        for (double nav : series.values()) {
            if (nav > peak) peak = nav;
            if (peak > 0) {
                double drawdown = nav / peak - 1.0;
                if (drawdown < worst) worst = drawdown;
            }
        }
        return worst;
    }

    /** How far below its all-time high the fund currently sits, as a negative fraction. */
    public static Double currentDrawdown(NavigableMap<LocalDate, Double> series) {
        if (series.isEmpty()) return null;
        double peak = -1;
        for (double nav : series.values()) if (nav > peak) peak = nav;
        double latest = series.lastEntry().getValue();
        return peak > 0 ? latest / peak - 1.0 : null;
    }

    /** Excess CAGR per unit of volatility. {@code riskFreeRate} is a fraction, e.g. 0.065. */
    public static Double sharpe(Double cagr, Double volatility, double riskFreeRate) {
        if (cagr == null || volatility == null || volatility <= 0) return null;
        return (cagr - riskFreeRate) / volatility;
    }

    /**
     * Like Sharpe but dividing by downside deviation only.
     * <p>
     * Upside volatility is not a risk an investor wants penalised, and the two measures disagree
     * most for exactly the funds worth distinguishing: those whose swings are mostly upward.
     */
    public static Double sortino(NavigableMap<LocalDate, Double> series, Double cagr,
                                 int years, double riskFreeRate) {
        if (cagr == null || series.isEmpty()) return null;
        LocalDate from = series.lastKey().minusYears(years);
        NavigableMap<LocalDate, Double> window = series.tailMap(from, true);
        if (window.size() < 30) return null;

        double previous = -1;
        double sumSquaredDownside = 0;
        int n = 0;
        for (double nav : window.values()) {
            if (previous > 0 && nav > 0) {
                double r = Math.log(nav / previous);
                if (r < 0) sumSquaredDownside += r * r;
                n++;
            }
            previous = nav;
        }
        if (n < 30 || sumSquaredDownside <= 0) return null;
        double downsideDeviation = Math.sqrt(sumSquaredDownside / n) * Math.sqrt(TRADING_DAYS);
        return (cagr - riskFreeRate) / downsideDeviation;
    }
}
