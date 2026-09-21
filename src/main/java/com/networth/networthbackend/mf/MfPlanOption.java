package com.networth.networthbackend.mf;

import lombok.experimental.UtilityClass;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Classifies a share class into its plan (Direct/Regular) and option (Growth/IDCW).
 * <p>
 * Both are parsed out of AMFI's {@code Scheme NAV Name}, not out of NAVAll's {@code Plan} and
 * {@code Option} columns: those are blank for 5,675 of NAVAll's 14,374 rows, and on exactly those
 * rows the scheme name is a bare fund name repeated across every share class, so a Direct+Growth
 * row is indistinguishable from an IDCW one. {@code Scheme NAV Name} always carries both, though in
 * no fixed order — all three of these occur:
 * <pre>
 *   Aditya Birla Sun Life Large &amp; Mid Cap Fund - Regular Growth
 *   Aditya Birla Sun Life Large &amp; Mid Cap Fund - Growth - Direct Plan
 *   Birla Income Plus-Institutional (Growth)
 * </pre>
 * Hence substring matching over the whole string rather than a positional split.
 */
@UtilityClass
public class MfPlanOption {

    public enum Plan {
        DIRECT,
        REGULAR,
        /** Pre-2013 schemes, from before the Direct/Regular split existed. */
        UNKNOWN
    }

    public enum Option {
        GROWTH,
        /** Income Distribution cum Capital Withdrawal — what used to be called Dividend. */
        IDCW,
        BONUS,
        UNKNOWN
    }

    private static final Pattern DIRECT = Pattern.compile("\\bDIRECT\\b");
    private static final Pattern REGULAR = Pattern.compile("\\bREGULAR\\b");

    /**
     * Matched before {@link #GROWTH_PATTERN}, because "Growth" appears inside IDCW share-class names
     * (e.g. "Direct Growth IDCW Payout"). AMFI has renamed this field repeatedly, so all of the
     * historical spellings have to be accepted at once.
     */
    private static final Pattern IDCW_PATTERN = Pattern.compile(
            "IDCW|IDWC|DIVIDEND|PAYOUT|REINVEST|INCOME DISTRIBUTION");

    private static final Pattern BONUS = Pattern.compile("\\bBONUS\\b");

    /** "Cumulative" is an old AMC name for the growth option. */
    private static final Pattern GROWTH_PATTERN = Pattern.compile("GROWTH|CUMULATIVE");

    /**
     * Share classes that still publish a NAV but are not investable funds, and would otherwise be
     * ranked as if they were.
     * <p>
     * "Unclaimed Redemption Plan - Growth" is a parking vehicle for unclaimed payouts; the
     * Institutional and Retail plans were closed to new money long ago but never wound up. The
     * {@code SG<n>} suffix marks a segregated (side-pocketed) portfolio carved out of a fund after
     * a credit event — AMFI writes these as "… Short Term Fund SG1 Direct Growth" rather than
     * spelling out "segregated", and they have no NAV series to compute anything from.
     */
    private static final Pattern NOT_INVESTABLE = Pattern.compile(
            "UNCLAIMED|DISCONTINUED|DEFUNCT|SEGREGATED|INSTITUTIONAL|SUPER INST|RETAIL PLAN"
                    + "|\\bSG ?\\d+\\b");

    private static String normalise(String navName) {
        if (navName == null) return "";
        return navName.replaceAll("\\s+", " ").trim().toUpperCase(Locale.ROOT);
    }

    public static Plan plan(String navName) {
        String s = normalise(navName);
        if (DIRECT.matcher(s).find()) return Plan.DIRECT;
        if (REGULAR.matcher(s).find()) return Plan.REGULAR;
        return Plan.UNKNOWN;
    }

    public static Option option(String navName) {
        String s = normalise(navName);
        if (IDCW_PATTERN.matcher(s).find()) return Option.IDCW;
        if (BONUS.matcher(s).find()) return Option.BONUS;
        if (GROWTH_PATTERN.matcher(s).find()) return Option.GROWTH;
        return Option.UNKNOWN;
    }

    /** True for closed, dead or unclaimed-money share classes that must stay out of rankings. */
    public static boolean isNotInvestable(String navName) {
        return NOT_INVESTABLE.matcher(normalise(navName)).find();
    }
}
