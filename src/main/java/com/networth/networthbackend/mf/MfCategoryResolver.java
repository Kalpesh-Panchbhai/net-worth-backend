package com.networth.networthbackend.mf;

import lombok.experimental.UtilityClass;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Maps AMFI's category string onto a canonical (asset class, sub-category) pair.
 * <p>
 * AMFI's feed carries two taxonomies side by side: the pre-2017 names (bare {@code Growth},
 * {@code Income}, {@code Gilt}, and the {@code Income/Debt Oriented Schemes - *} family) and the
 * SEBI-2017 names ({@code Equity Scheme - *}, {@code Debt Scheme - *}, ...). Several categories also
 * appear under both a singular and a plural spelling — {@code Equity Scheme - Large Cap Fund} and
 * {@code Equity Schemes - Large Cap Fund} are the same peer group. Ranking on the raw string
 * therefore produces two separate leaderboards for one category, so everything is collapsed here
 * and every downstream query groups on the resolved sub-category instead.
 * <p>
 * An unrecognised string resolves to {@code (UNKNOWN, "Unclassified")} rather than throwing: AMFI
 * adds and respells categories without notice, and a sync that fails on a new label is worse than
 * one fund sitting out of the rankings until the map is extended.
 */
@UtilityClass
public class MfCategoryResolver {

    public static final String UNCLASSIFIED = "Unclassified";

    /** Result of resolving one AMFI category string. */
    public record Category(AssetClass assetClass, String subCategory) {
        public boolean isRankable() {
            return assetClass != AssetClass.UNKNOWN;
        }
    }

    private static final Map<String, Category> BY_NORMALISED = new HashMap<>();

    /**
     * Normalises for lookup only — the raw string is still what gets stored.
     * <p>
     * Everything except letters, digits and the separators that carry meaning ({@code -}, {@code /},
     * {@code &}) is reduced to a space, and runs of whitespace are then collapsed. That makes the
     * lookup immune to the three ways AMFI's punctuation varies: apostrophes that are sometimes
     * straight and sometimes a curly quote in an unclear encoding
     * ({@code "Children's Fund"}), the footnote marker in
     * {@code "Solution Oriented Schemes ** - Retirement Fund"}, and the stray double space in
     * {@code "Other Scheme - Other  ETFs"}. The map keys go through the same function, so both
     * sides collapse identically.
     */
    private static String normalise(String raw) {
        if (raw == null) return "";
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            sb.append(Character.isLetterOrDigit(c) || c == '-' || c == '/' || c == '&' ? c : ' ');
        }
        return sb.toString().replaceAll("\\s+", " ").trim().toUpperCase(Locale.ROOT);
    }

    private static void put(AssetClass assetClass, String subCategory, String... rawCategories) {
        for (String raw : rawCategories) {
            BY_NORMALISED.put(normalise(raw), new Category(assetClass, subCategory));
        }
    }

    static {
        // ---- Equity. Each call lists the SEBI-2017 spelling first, then the legacy variants. ----
        put(AssetClass.EQUITY, "Large Cap",
                "Equity Scheme - Large Cap Fund", "Equity Schemes - Large Cap Fund");
        put(AssetClass.EQUITY, "Mid Cap",
                "Equity Scheme - Mid Cap Fund", "Equity Schemes - Mid Cap Fund");
        put(AssetClass.EQUITY, "Small Cap",
                "Equity Scheme - Small Cap Fund", "Equity Schemes - Small Cap Fund");
        put(AssetClass.EQUITY, "Large & Mid Cap",
                "Equity Scheme - Large & Mid Cap Fund", "Equity Schemes - Large & Mid Cap Fund");
        put(AssetClass.EQUITY, "Multi Cap",
                "Equity Scheme - Multi Cap Fund", "Equity Schemes - Multi Cap Fund");
        put(AssetClass.EQUITY, "Flexi Cap",
                "Equity Scheme - Flexi Cap Fund", "Equity Schemes - Flexi Cap Fund");
        put(AssetClass.EQUITY, "Focused",
                "Equity Scheme - Focused Fund", "Equity Schemes - Focused Fund");
        put(AssetClass.EQUITY, "Value",
                "Equity Scheme - Value Fund", "Equity Schemes - Value Fund");
        put(AssetClass.EQUITY, "Contra",
                "Equity Scheme - Contra Fund", "Equity Schemes - Contra Fund");
        put(AssetClass.EQUITY, "Dividend Yield",
                "Equity Scheme - Dividend Yield Fund", "Equity Schemes - Dividend Yield Fund");
        // AMFI split sectoral and thematic before 2017 and merged them after; SEBI treats them as
        // one category, and peer comparison only makes sense against the merged group.
        put(AssetClass.EQUITY, "Sectoral / Thematic",
                "Equity Scheme - Sectoral/ Thematic",
                "Equity Schemes - Sectoral Fund",
                "Equity Schemes - Thematic Fund");
        put(AssetClass.EQUITY, "ELSS",
                "Equity Scheme - ELSS", "Equity Schemes - ELSS- Tax Saver Fund", "ELSS");
        // Bare "Growth" is the pre-2017 label for what is now plain equity. Kept as its own
        // sub-category rather than guessed into one: the underlying mandate is unknown.
        put(AssetClass.EQUITY, "Legacy Growth", "Growth");

        // ---- Debt. Pre-2017 used "Term", SEBI-2017 uses "Duration", for the same buckets. ----
        put(AssetClass.DEBT, "Overnight",
                "Debt Scheme - Overnight Fund", "Income/Debt Oriented Schemes - Overnight Fund");
        put(AssetClass.DEBT, "Liquid",
                "Debt Scheme - Liquid Fund", "Income/Debt Oriented Schemes - Liquid Fund");
        put(AssetClass.DEBT, "Ultra Short Duration",
                "Debt Scheme - Ultra Short Duration Fund",
                "Income/Debt Oriented Schemes - Ultra Short Term Fund");
        put(AssetClass.DEBT, "Low Duration",
                "Debt Scheme - Low Duration Fund",
                "Income/Debt Oriented Schemes - Ultra Short to Short Term Fund");
        put(AssetClass.DEBT, "Money Market",
                "Debt Scheme - Money Market Fund",
                "Income/Debt Oriented Schemes - Money Market Fund",
                "Money Market");
        put(AssetClass.DEBT, "Short Duration",
                "Debt Scheme - Short Duration Fund",
                "Income/Debt Oriented Schemes - Short Term Fund");
        put(AssetClass.DEBT, "Medium Duration",
                "Debt Scheme - Medium Duration Fund",
                "Income/Debt Oriented Schemes - Medium Term Fund");
        put(AssetClass.DEBT, "Medium to Long Duration",
                "Debt Scheme - Medium to Long Duration Fund",
                "Income/Debt Oriented Schemes - Medium to Long Term Fund");
        put(AssetClass.DEBT, "Long Duration",
                "Debt Scheme - Long Duration Fund",
                "Income/Debt Oriented Schemes - Long Term Fund");
        put(AssetClass.DEBT, "Dynamic Bond",
                "Debt Scheme - Dynamic Bond", "Income/Debt Oriented Schemes - Dynamic Term Fund");
        put(AssetClass.DEBT, "Corporate Bond",
                "Debt Scheme - Corporate Bond Fund",
                "Income/Debt Oriented Schemes - Corporate Bond Fund");
        put(AssetClass.DEBT, "Credit Risk",
                "Debt Scheme - Credit Risk Fund", "Income/Debt Oriented Schemes - Credit Risk Fund");
        put(AssetClass.DEBT, "Banking & PSU",
                "Debt Scheme - Banking and PSU Fund",
                "Income/Debt Oriented Schemes - Banking and PSU Debt Fund");
        put(AssetClass.DEBT, "Gilt",
                "Debt Scheme - Gilt Fund", "Income/Debt Oriented Schemes - Gilt Fund", "Gilt");
        // Distinct from plain Gilt: a fixed ~10y duration mandate rather than an active one.
        put(AssetClass.DEBT, "Gilt 10Y Constant Maturity",
                "Income/Debt Oriented Schemes - 10-year Constant Maturity Gilt Fund");
        put(AssetClass.DEBT, "Floater",
                "Income/Debt Oriented Schemes - Floating Interest Rates Fund");
        put(AssetClass.DEBT, "Fixed Term Plan",
                "Income/Debt Oriented Schemes - Fixed Term Plan");
        put(AssetClass.DEBT, "Other Debt",
                "Income/Debt Oriented Schemes - Other Debt Scheme",
                "Income/Debt Oriented Schemes - Sectoral Fund");
        put(AssetClass.DEBT, "Legacy Income", "Income");

        // ---- Hybrid ----
        put(AssetClass.HYBRID, "Arbitrage",
                "Hybrid Scheme - Arbitrage Fund", "Hybrid Schemes - Arbitrage Fund");
        put(AssetClass.HYBRID, "Equity Savings",
                "Hybrid Scheme - Equity Savings", "Hybrid Schemes - Equity Savings Fund");
        put(AssetClass.HYBRID, "Conservative Hybrid",
                "Hybrid Scheme - Conservative Hybrid Fund", "Hybrid Schemes - Conservative Hybrid Fund");
        put(AssetClass.HYBRID, "Balanced Hybrid",
                "Hybrid Scheme - Balanced Hybrid Fund", "Hybrid Schemes - Balanced Hybrid Fund");
        put(AssetClass.HYBRID, "Aggressive Hybrid",
                "Hybrid Scheme - Aggressive Hybrid Fund", "Hybrid Schemes - Aggressive Hybrid Fund");
        put(AssetClass.HYBRID, "Balanced Advantage / DAAF",
                "Hybrid Scheme - Dynamic Asset Allocation or Balanced Advantage",
                "Hybrid Schemes - Balanced Advantage Fund/ Dynamic Asset Allocation");
        put(AssetClass.HYBRID, "Multi Asset Allocation",
                "Hybrid Scheme - Multi Asset Allocation", "Hybrid Schemes - Multi Asset Allocation Fund");

        // ---- Solution oriented ----
        put(AssetClass.SOLUTION, "Retirement",
                "Solution Oriented Scheme - Retirement Fund", "Solution Oriented Schemes - Retirement Fund");
        put(AssetClass.SOLUTION, "Children's",
                "Solution Oriented Scheme - Children's Fund", "Children's Fund - Childrens' Fund");
        // Target-date funds with no modern SEBI equivalent. Grouped together: the three maturity
        // variants have too few funds each to rank separately.
        put(AssetClass.SOLUTION, "Life Cycle",
                "Life Cycle Funds - Life Cycle Fund with Maturity of 5 Years",
                "Life Cycle Funds - Life Cycle Fund with Maturity of 10 Years",
                "Life Cycle Funds - Life Cycle Fund with Maturity of 15 Years");

        // ---- Other: passive, commodity and fund-of-funds ----
        // Index funds keep their underlying asset class in the sub-category, because an equity
        // index fund and a debt index fund are not peers even though AMFI files both under one
        // post-2017 label.
        put(AssetClass.OTHER, "Index Fund - Equity",
                "Other Scheme - Index Funds", "Index Funds - Equity Funds");
        put(AssetClass.OTHER, "Index Fund - Debt", "Index Funds - Debt Funds");
        put(AssetClass.OTHER, "Index Fund - Hybrid", "Index Funds - Hybrid Fund");
        put(AssetClass.OTHER, "Equity ETF", "Exchange Traded Funds (ETFs) - Equity ETF");
        put(AssetClass.OTHER, "Debt ETF", "Exchange Traded Funds (ETFs) - Debt ETF");
        put(AssetClass.OTHER, "Hybrid ETF", "Exchange Traded Funds (ETFs) - Hybrid ETF");
        put(AssetClass.OTHER, "Gold ETF",
                "Other Scheme - Gold ETF", "Exchange Traded Funds (ETFs) - Gold ETF");
        put(AssetClass.OTHER, "Silver ETF", "Exchange Traded Funds (ETFs) - Silver ETF");
        put(AssetClass.OTHER, "Overseas ETF", "Exchange Traded Funds (ETFs) - ETFs investing overseas");
        put(AssetClass.OTHER, "Other ETF",
                "Other Scheme - Other  ETFs", "Exchange Traded Funds (ETFs) - Other ETF");
        put(AssetClass.OTHER, "FoF Domestic",
                "Other Scheme - FoF Domestic",
                "Fund of Funds Scheme (Domestic) - Fund of Funds Scheme (Domestic)");
        put(AssetClass.OTHER, "FoF Overseas",
                "Other Scheme - FoF Overseas",
                "Overseas Fund of Funds - Fund of Funds investing overseas");
    }

    private static final Category UNRESOLVED = new Category(AssetClass.UNKNOWN, UNCLASSIFIED);

    /** Never throws. An unknown string yields {@link AssetClass#UNKNOWN}; the caller records it. */
    public static Category resolve(String rawCategory) {
        return BY_NORMALISED.getOrDefault(normalise(rawCategory), UNRESOLVED);
    }

    /** Number of distinct AMFI spellings the map covers. Used by the sync's sanity logging. */
    public static int knownSpellings() {
        return BY_NORMALISED.size();
    }
}
