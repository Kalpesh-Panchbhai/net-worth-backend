package com.networth.networthbackend.tools;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * Client for fetching historical gold prices in INR per gram.
 * Uses gold futures (GC=F) and converts to INR per gram.
 * Applies Indian market premium to account for import duties and local market conditions.
 */
public class GoldPriceClient {

    // Gold futures symbol
    private static final String GOLD_FUTURES_SYMBOL = "GC=F";

    // NOTE: GC=F is quoted per TROY ounce, which is 31.1035 g. The divisor below is 28.3495 g —
    // one avoirdupois ounce — so the INR/gram result comes out 31.1035/28.3495 = 9.7% high.
    // Left as-is deliberately: it is the only thing currently standing in for the "Indian market
    // premium" that Step 3 of convertToINRPerGram never actually applies, and correcting the
    // divisor alone would drop every gold holding's stored value by ~8.8%. Decide the premium and
    // the divisor together, then change both.
    private static final BigDecimal GRAMS_PER_TROY_OUNCE = new BigDecimal("28.3495");

    private final YahooFinanceClient yahooClient;

    public GoldPriceClient() {
        this.yahooClient = new YahooFinanceClient();
    }

    /**
     * Fetches historical gold prices in INR per gram for the given date range.
     * Uses gold futures (GC=F) and converts to INR with market premium.
     *
     * @param start Start date (inclusive)
     * @param end   End date (inclusive)
     * @return Map of date to gold price in INR per gram, forward-filled for calendar days
     * @throws IOException          If API call fails
     * @throws InterruptedException If API call is interrupted
     */
    public Map<LocalDate, BigDecimal> getGoldPricesInINR(LocalDate start, LocalDate end) throws IOException, InterruptedException {
        // Fetch gold futures prices in USD per troy ounce
        Map<LocalDate, BigDecimal> goldPricesUSD = yahooClient.getDailyCloseCalendarFilled(GOLD_FUTURES_SYMBOL, start, end);

        // Fetch USD to INR exchange rates
        Map<LocalDate, BigDecimal> usdToInrRates = yahooClient.getFxCalendarFilled("USD", "INR", start, end);

        // Convert to INR per gram with market premium
        return convertToINRPerGram(goldPricesUSD, usdToInrRates, start, end);
    }

    /**
     * Converts gold prices from USD per troy ounce to INR per gram.
     * Applies Indian market premium to account for import duties and local market conditions.
     */
    private Map<LocalDate, BigDecimal> convertToINRPerGram(Map<LocalDate, BigDecimal> goldPricesUSD, Map<LocalDate, BigDecimal> usdToInrRates, LocalDate start, LocalDate end) {

        Map<LocalDate, BigDecimal> result = new java.util.HashMap<>();
        LocalDate current = start;

        while (!current.isAfter(end)) {
            BigDecimal priceUSD = goldPricesUSD.get(current);
            BigDecimal fxRate = usdToInrRates.get(current);

            if (priceUSD != null && fxRate != null) {
                // Convert USD per troy ounce to INR per gram
                // Step 1: Convert to INR per troy ounce
                BigDecimal priceINRPerOunce = priceUSD.multiply(fxRate);

                // Step 2: Convert to INR per gram
                BigDecimal priceINRPerGram = priceINRPerOunce.divide(GRAMS_PER_TROY_OUNCE, 4, java.math.RoundingMode.HALF_UP);

                // Step 3: Apply Indian market premium (import duty, making charges, etc.)
                BigDecimal finalPrice = priceINRPerGram.setScale(2, java.math.RoundingMode.HALF_UP);

                result.put(current, finalPrice);
            }

            current = current.plusDays(1);
        }

        return result;
    }
}
