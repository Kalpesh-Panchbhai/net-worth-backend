package com.networth.networthbackend.mf;

import java.util.Locale;

/**
 * The vocabulary stored in {@code mf_metric.horizon} and {@code mf_metric.metric}.
 * <p>
 * The columns are VARCHAR so a new metric needs no migration, but the values a metric can take are
 * still a closed set in code — otherwise a typo in one job silently creates a parallel metric that
 * no reader ever finds.
 */
public final class MfMetricKey {

    private MfMetricKey() {
    }

    /** Measurement window. {@code SI} is since inception, so it has no fixed length. */
    public enum Horizon {
        M1(0, "1M"),
        M3(0, "3M"),
        M6(0, "6M"),
        Y1(1, "1Y"),
        Y3(3, "3Y"),
        Y5(5, "5Y"),
        Y7(7, "7Y"),
        Y10(10, "10Y"),
        SI(0, "SI");

        /** Whole years in the window, or 0 for sub-year windows and since-inception. */
        public final int years;
        public final String code;

        Horizon(int years, String code) {
            this.years = years;
            this.code = code;
        }
    }

    /**
     * What is being measured.
     *
     * @see #higherIsBetter as the single place ranking direction is decided
     */
    public enum Metric {
        CAGR("cagr"),
        ABSOLUTE("absolute"),

        /** Mean of every rolling window of the horizon's length. */
        ROLLING_AVG("rolling_avg"),
        /** Worst outcome an investor arriving on any single day actually got. */
        ROLLING_WORST("rolling_worst"),
        ROLLING_BEST("rolling_best"),
        ROLLING_SHARE_NEGATIVE("rolling_share_negative"),
        ROLLING_SHARE_ABOVE_12("rolling_share_above_12"),
        /** How many windows the average is over — a 3-window average is not comparable to a 2,000-window one. */
        ROLLING_WINDOWS("rolling_windows"),

        VOLATILITY("volatility"),
        SHARPE("sharpe"),
        SORTINO("sortino"),
        MAX_DRAWDOWN("max_drawdown"),
        CURRENT_DRAWDOWN("current_drawdown");

        public final String code;

        Metric(String code) {
            this.code = code;
        }

        /**
         * Sort direction for ranking.
         * <p>
         * Drawdowns are stored as negative fractions, so a larger value is genuinely better and the
         * plain comparison is correct; volatility and share-negative are the metrics where low
         * wins. Centralised here so no caller has to remember which way a metric points.
         */
        public boolean higherIsBetter() {
            return switch (this) {
                case VOLATILITY, ROLLING_SHARE_NEGATIVE -> false;
                default -> true;
            };
        }

        /** True when the value is a count rather than a rate, so readers do not render it as a %. */
        public boolean isCount() {
            return this == ROLLING_WINDOWS;
        }
    }

    /** Parses a stored code back to its enum, case-insensitively. */
    public static Metric metric(String code) {
        for (Metric m : Metric.values()) {
            if (m.code.equalsIgnoreCase(code)) return m;
        }
        throw new IllegalArgumentException("Unknown metric: " + code);
    }

    public static Horizon horizon(String code) {
        String upper = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
        for (Horizon h : Horizon.values()) {
            if (h.code.equals(upper)) return h;
        }
        throw new IllegalArgumentException("Unknown horizon: " + code);
    }
}
