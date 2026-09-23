package com.stock.analyzer.backtest.ranking;

/**
 * Probabilistic Sharpe Ratio (PSR) and Deflated Sharpe Ratio (DSR), per Bailey
 * &amp; López de Prado (2014).
 *
 * <p>The raw Sharpe ratio is the average of a finite return sample, so it is
 * itself an estimate with non-trivial sampling error. When a researcher picks
 * the best of N candidate strategies, the observed Sharpe of the winner is
 * upward-biased by selection. PSR corrects the Sharpe for sample length and
 * non-Normality (skew, kurtosis). DSR additionally corrects for the
 * multiple-testing bias from picking the best of N trials.
 *
 * <p>Reference values for sanity-checking the implementation:
 * <ul>
 *   <li>{@code cdf(0)} = 0.5</li>
 *   <li>{@code cdf(1.96)} ≈ 0.975 (one-sided 95% CI)</li>
 *   <li>{@code inverseCdf(0.975)} ≈ 1.96</li>
 *   <li>{@code expectedMaxSharpe(1.0, 1000)} ≈ 3.24 (annualised)</li>
 * </ul>
 *
 * <p>All Sharpe values in the public API are <b>annualised</b>. Internal
 * conversion to per-period units is handled by the formulas. Skew and kurtosis
 * must be computed from <b>per-period (daily)</b> returns, with kurtosis as
 * Pearson's (≈ 3 for Normal).
 */
public final class DeflatedSharpe {

    private DeflatedSharpe() {}

    /** Euler-Mascheroni constant. */
    private static final double GAMMA_E = 0.5772156649015329;

    /** Trading periods per year. Daily bars → 252. */
    public static final int PERIODS_PER_YEAR = 252;

    // ── Normal CDF (Abramowitz &amp; Stegun 26.2.17, accuracy ~1e-7) ─────

    public static double cdf(double z) {
        if (Double.isNaN(z)) return Double.NaN;
        if (z < 0) return 1.0 - cdf(-z);
        double t = 1.0 / (1.0 + 0.2316419 * z);
        double phi = Math.exp(-z * z / 2.0) / Math.sqrt(2.0 * Math.PI);
        double poly = t * (0.319381530
                  + t * (-0.356563782
                  + t * ( 1.781477937
                  + t * (-1.821255978
                  + t *   1.330274429))));
        return 1.0 - phi * poly;
    }

    // ── Inverse Normal CDF (Peter J. Acklam's algorithm, accuracy ~1e-9) ───

    public static double inverseCdf(double p) {
        if (p <= 0 || p >= 1) {
            if (p == 0) return Double.NEGATIVE_INFINITY;
            if (p == 1) return Double.POSITIVE_INFINITY;
            throw new IllegalArgumentException("p must be in (0,1), got " + p);
        }
        final double[] a = {-3.969683028665376e+01,  2.209460984245205e+02,
                            -2.759285104469687e+02,  1.383577518672690e+02,
                            -3.066479806614716e+01,  2.506628277459239e+00};
        final double[] b = {-5.447609879822406e+01,  1.615858368580409e+02,
                            -1.556989798598866e+02,  6.680131188771972e+01,
                            -1.328068155288572e+01};
        final double[] c = {-7.784894002430293e-03, -3.223964580411365e-01,
                            -2.400758277161838e+00, -2.549732539343734e+00,
                             4.374664141464968e+00,  2.938163982698783e+00};
        final double[] d = { 7.784695709041462e-03,  3.224671290700398e-01,
                             2.445134137142996e+00,  3.754408661907416e+00};
        final double pLow = 0.02425, pHigh = 1 - pLow;
        double q, r;
        if (p < pLow) {
            q = Math.sqrt(-2 * Math.log(p));
            return (((((c[0]*q+c[1])*q+c[2])*q+c[3])*q+c[4])*q+c[5]) /
                   ((((d[0]*q+d[1])*q+d[2])*q+d[3])*q + 1);
        } else if (p <= pHigh) {
            q = p - 0.5; r = q * q;
            return (((((a[0]*r+a[1])*r+a[2])*r+a[3])*r+a[4])*r+a[5]) * q /
                   (((((b[0]*r+b[1])*r+b[2])*r+b[3])*r+b[4])*r + 1);
        } else {
            q = Math.sqrt(-2 * Math.log(1 - p));
            return -(((((c[0]*q+c[1])*q+c[2])*q+c[3])*q+c[4])*q+c[5]) /
                     ((((d[0]*q+d[1])*q+d[2])*q+d[3])*q + 1);
        }
    }

    // ── Probabilistic Sharpe Ratio ────────────────────────────────────

    /**
     * Probability that the true Sharpe exceeds {@code srStarAnnualised}, given
     * the observed sample. Inputs in annualised Sharpe units; the formula
     * converts to per-period internally.
     *
     * <p>{@code skew} and {@code kurtosis} must come from the same per-period
     * returns used to compute {@code srAnnualised}. Kurtosis is Pearson's
     * (≈ 3 for Normal). The formula is invariant to mean/variance scaling but
     * requires correct units, so be careful.
     */
    public static double psr(double srAnnualised, double srStarAnnualised,
                             int observations, double skew, double kurtosis) {
        if (observations < 2) return Double.NaN;
        double sqrtP = Math.sqrt(PERIODS_PER_YEAR);
        double srP    = srAnnualised     / sqrtP;
        double srStarP= srStarAnnualised / sqrtP;
        double denom  = Math.sqrt(1.0 - skew * srP + (kurtosis - 1.0) / 4.0 * srP * srP);
        if (!Double.isFinite(denom) || denom <= 0) return Double.NaN;
        double z = (srP - srStarP) * Math.sqrt(observations - 1) / denom;
        return cdf(z);
    }

    // ── Expected maximum Sharpe under H₀ (multiple-testing threshold) ─

    /**
     * E[max SR] over N independent trials whose true Sharpe is zero and whose
     * estimated Sharpes have variance {@code varSharpe} (annualised units²).
     * This is the threshold the observed Sharpe must beat to be considered
     * skill rather than search luck.
     */
    public static double expectedMaxSharpe(double varSharpe, int N) {
        if (N < 2 || varSharpe <= 0) return 0;
        double phiInvA = inverseCdf(1.0 - 1.0 / N);
        double phiInvB = inverseCdf(1.0 - 1.0 / (N * Math.E));
        return Math.sqrt(varSharpe) * ((1 - GAMMA_E) * phiInvA + GAMMA_E * phiInvB);
    }

    // ── Deflated Sharpe Ratio ─────────────────────────────────────────

    /**
     * Probability that the observed Sharpe is real, after correcting for
     * having picked the best out of {@code nTrials} candidate strategies.
     *
     * <p>{@code varSharpeAcrossTrials} should be the sample variance of the
     * Sharpe estimates produced by the N trials (annualised, units²). When
     * the trial population is unavailable, a conservative default in the
     * range 0.25–1.0 is commonly used.
     */
    public static double dsr(double srAnnualised, int nTrials, double varSharpeAcrossTrials,
                             int observations, double skew, double kurtosis) {
        double srStar = expectedMaxSharpe(varSharpeAcrossTrials, nTrials);
        return psr(srAnnualised, srStar, observations, skew, kurtosis);
    }

    // ── Sample statistics helpers ─────────────────────────────────────

    /** Sample skewness (Fisher-Pearson) of {@code x}, biased estimator m₃/m₂^(3/2). */
    public static double skewness(double[] x) {
        int n = x.length;
        if (n < 3) return 0;
        double mean = mean(x);
        double m2 = 0, m3 = 0;
        for (double v : x) {
            double d = v - mean;
            m2 += d * d;
            m3 += d * d * d;
        }
        m2 /= n; m3 /= n;
        if (m2 <= 0) return 0;
        return m3 / Math.pow(m2, 1.5);
    }

    /** Pearson's kurtosis (≈ 3 for Normal). Use this — not excess kurtosis. */
    public static double kurtosis(double[] x) {
        int n = x.length;
        if (n < 4) return 3;
        double mean = mean(x);
        double m2 = 0, m4 = 0;
        for (double v : x) {
            double d = v - mean;
            m2 += d * d;
            m4 += d * d * d * d;
        }
        m2 /= n; m4 /= n;
        if (m2 <= 0) return 3;
        return m4 / (m2 * m2);
    }

    public static double mean(double[] x) {
        if (x.length == 0) return 0;
        double s = 0;
        for (double v : x) s += v;
        return s / x.length;
    }

    /** Sample standard deviation with Bessel correction. */
    public static double stdDev(double[] x) {
        int n = x.length;
        if (n < 2) return 0;
        double mean = mean(x);
        double s = 0;
        for (double v : x) s += (v - mean) * (v - mean);
        return Math.sqrt(s / (n - 1));
    }

    /** Variance of array {@code x} with Bessel correction. */
    public static double variance(double[] x) {
        double s = stdDev(x);
        return s * s;
    }

    /** Annualised Sharpe from per-period (daily) returns. r_f assumed 0. */
    public static double annualisedSharpe(double[] returns) {
        double s = stdDev(returns);
        if (s == 0) return 0;
        return mean(returns) / s * Math.sqrt(PERIODS_PER_YEAR);
    }
}
