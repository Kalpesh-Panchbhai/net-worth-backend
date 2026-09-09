package com.networth.networthbackend.common;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Money-weighted annualised return (XIRR) over dated cash flows.
 * <p>
 * Computed server-side so it is derived from the same converted amounts the client is shown, and
 * so a page listing many accounts does not have to fetch every account's transactions to get it.
 * Newton-Raphson first, bisection as a fallback when the derivative is unusable.
 */
public final class Xirr {

    private static final int MAX_ITERATIONS = 100;
    private static final double TOLERANCE = 1e-7;
    private static final double RATE_FLOOR = -0.9999;
    private static final double RATE_CEILING = 10.0;
    private static final double DAYS_PER_YEAR = 365.0;

    private Xirr() {
    }

    /** A cash flow: negative for money invested, positive for money returned or current value. */
    public record Flow(LocalDate date, double amount) {
    }

    /**
     * @return the annualised rate as a fraction (0.12 == 12%), or null when the flows cannot
     * produce one (fewer than two flows, or no sign change).
     */
    public static Double of(List<Flow> flows) {
        if (flows == null || flows.size() < 2) return null;
        boolean positive = false;
        boolean negative = false;
        for (Flow f : flows) {
            if (f.amount() > 0) positive = true;
            else if (f.amount() < 0) negative = true;
        }
        if (!positive || !negative) return null;

        List<Flow> sorted = new ArrayList<>(flows);
        sorted.sort(Comparator.comparing(Flow::date));
        LocalDate t0 = sorted.get(0).date();
        // All flows fall on one date: the return spans zero elapsed time, so xnpv is constant in
        // the rate and there is no IRR to annualise. Returning early avoids handing back the Newton
        // seed as a bogus rate when those flows happen to net to zero at it (e.g. a chart point on
        // the same day as the only investment, with value still equal to what was put in).
        if (t0.equals(sorted.get(sorted.size() - 1).date())) return null;

        Double newton = newtonRaphson(sorted, t0);
        if (newton != null) return newton;
        return bisect(sorted, t0);
    }

    private static Double newtonRaphson(List<Flow> flows, LocalDate t0) {
        double rate = 0.1;
        for (int i = 0; i < MAX_ITERATIONS; i++) {
            double value = xnpv(rate, flows, t0);
            if (Math.abs(value) < TOLERANCE) return rate;
            double slope = xnpvDerivative(rate, flows, t0);
            if (Math.abs(slope) < 1e-14) return null;
            double next = rate - value / slope;
            if (!Double.isFinite(next)) return null;
            if (Math.abs(next - rate) < 1e-10) return next;
            rate = Math.max(next, RATE_FLOOR);
        }
        return null;
    }

    private static Double bisect(List<Flow> flows, LocalDate t0) {
        double low = RATE_FLOOR;
        double high = RATE_CEILING;
        double fLow = xnpv(low, flows, t0);
        double fHigh = xnpv(high, flows, t0);
        if (!Double.isFinite(fLow) || !Double.isFinite(fHigh) || fLow * fHigh > 0) return null;
        for (int i = 0; i < MAX_ITERATIONS; i++) {
            double mid = (low + high) / 2;
            double fMid = xnpv(mid, flows, t0);
            if (Math.abs(fMid) < TOLERANCE) return mid;
            if (fMid * fLow < 0) high = mid;
            else low = mid;
        }
        return (low + high) / 2;
    }

    private static double xnpv(double rate, List<Flow> flows, LocalDate t0) {
        double sum = 0;
        for (Flow f : flows) {
            sum += f.amount() / Math.pow(1 + rate, years(t0, f.date()));
        }
        return sum;
    }

    private static double xnpvDerivative(double rate, List<Flow> flows, LocalDate t0) {
        double sum = 0;
        for (Flow f : flows) {
            double t = years(t0, f.date());
            sum += (-t * f.amount()) / Math.pow(1 + rate, t + 1);
        }
        return sum;
    }

    private static double years(LocalDate from, LocalDate to) {
        return (to.toEpochDay() - from.toEpochDay()) / DAYS_PER_YEAR;
    }
}
