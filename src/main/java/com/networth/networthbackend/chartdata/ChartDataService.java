package com.networth.networthbackend.chartdata;

import com.networth.networthbackend.fx.FxRates;
import com.networth.networthbackend.fx.FxService;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Slf4j
public class ChartDataService {

    private static final int DISPLAY_SCALE = 2;

    private final ChartDataRepository repository = new ChartDataRepository();

    /**
     * Converts a single-currency chart series (holding or account) into the preferred currency,
     * applying each point's own date FX rate — a historical point is worth what it was worth then.
     * Mutates and returns the same list.
     */
    public List<ChartData> convertSeries(List<ChartData> data, String sourceCurrency, String preferredCurrency) {
        if (data == null || data.isEmpty()) return data;
        String preferred = FxService.normalize(preferredCurrency);
        String source = FxService.normalize(sourceCurrency);
        LocalDate start = data.get(0).getDate();
        LocalDate end = data.get(data.size() - 1).getDate();
        FxRates rates = FxService.rates(source, preferred, start, end);
        for (ChartData cd : data) {
            BigDecimal value = rates.convertAsOf(cd.getValue(), cd.getDate());
            BigDecimal invested = rates.convertAsOf(cd.getInvested(), cd.getDate());
            if (value == null || invested == null) {
                cd.setDisplayCurrency(source); // honest label; the point keeps its native amounts
                continue;
            }
            cd.setValue(scale(value));
            cd.setInvested(scale(invested));
            cd.setDisplayCurrency(preferred);
        }
        return data;
    }

    /**
     * Builds a watchlist chart series in the preferred currency, converting each member account's
     * native series per date <em>before</em> summing across accounts.
     * <p>
     * Every member's series comes from one query. An account whose currency has no rate is left
     * out of the total and logged, rather than added in raw: a chart labelled in the preferred
     * currency must not silently be a sum of mixed currencies.
     */
    public List<ChartData> getWatchlistChartDataConverted(long watchlistId, String timePeriod, String preferredCurrency) {
        String preferred = FxService.normalize(preferredCurrency);
        try {
            TimePeriod period = TimePeriod.fromCode(timePeriod);
            LocalDate endDate = LocalDate.now();
            LocalDate earliest = repository.getEarliestDate(EntityType.WATCHLIST, watchlistId);
            if (earliest == null) return new ArrayList<>();
            LocalDate startDate = calculateEffectiveStartDate(period, endDate, () -> earliest);

            List<ChartDataRepository.AccountSeriesRow> rows =
                    repository.getWatchlistSeriesByAccount(watchlistId, startDate, endDate);
            if (rows.isEmpty()) return new ArrayList<>();

            Map<Long, List<ChartData>> byAccount = new LinkedHashMap<>();
            Map<Long, String> currencyByAccount = new LinkedHashMap<>();
            for (ChartDataRepository.AccountSeriesRow row : rows) {
                currencyByAccount.putIfAbsent(row.accountId(), row.currency());
                byAccount.computeIfAbsent(row.accountId(), id -> new ArrayList<>())
                        .add(ChartData.builder().date(row.date()).value(row.value()).invested(row.invested()).build());
            }

            Map<String, FxRates> ratesBySource = FxService.rates(currencyByAccount.values(), preferred, startDate, endDate);

            TreeMap<LocalDate, BigDecimal[]> totals = new TreeMap<>();
            Set<String> unconvertible = new HashSet<>();
            for (Map.Entry<Long, List<ChartData>> entry : byAccount.entrySet()) {
                String source = FxService.normalize(currencyByAccount.get(entry.getKey()));
                FxRates rates = ratesBySource.getOrDefault(source, FxRates.unavailable());
                if (!rates.isResolvable()) {
                    unconvertible.add(source);
                    continue;
                }
                for (ChartData cd : generateChartDataWithForwardFill(entry.getValue(), startDate, endDate)) {
                    BigDecimal value = rates.convertAsOf(cd.getValue(), cd.getDate());
                    BigDecimal invested = rates.convertAsOf(cd.getInvested(), cd.getDate());
                    if (value == null || invested == null) {
                        unconvertible.add(source);
                        break;
                    }
                    BigDecimal[] sums = totals.computeIfAbsent(cd.getDate(), d -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
                    sums[0] = sums[0].add(value);
                    sums[1] = sums[1].add(invested);
                }
            }
            if (!unconvertible.isEmpty()) {
                log.warn("Watchlist {} chart: excluded accounts in {} from the total, no FX rate to {}",
                        watchlistId, unconvertible, preferred);
            }

            List<ChartData> result = new ArrayList<>(totals.size());
            long id = 1;
            for (Map.Entry<LocalDate, BigDecimal[]> e : totals.entrySet()) {
                result.add(ChartData.builder().id(id++).date(e.getKey())
                        .value(scale(e.getValue()[0])).invested(scale(e.getValue()[1]))
                        .displayCurrency(preferred).build());
            }
            return result;
        } catch (Exception e) {
            log.warn("Error building converted watchlist chart data for ID {}: {}. Returning empty array.", watchlistId, e.getMessage());
            return new ArrayList<>();
        }
    }

    private static BigDecimal scale(BigDecimal v) {
        return v.setScale(DISPLAY_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Generic method to get chart data for any entity type
     * Returns empty array if entity doesn't exist or has no data
     */
    public List<ChartData> getChartData(EntityType entityType, long entityId, String timePeriod) throws Exception {
        try {
            TimePeriod period = TimePeriod.fromCode(timePeriod);
            LocalDate endDate = LocalDate.now();

            // Get earliest date - if null, entity has no data
            LocalDate earliestAvailableDate = repository.getEarliestDate(entityType, entityId);
            if (earliestAvailableDate == null) {
                log.debug("No data available for {} with ID {}, returning empty array", entityType.getCode(), entityId);
                return new ArrayList<>();
            }

            LocalDate startDate = calculateEffectiveStartDate(period, endDate, () -> earliestAvailableDate);

            List<ChartData> rawData = repository.getChartData(entityType, entityId, startDate, endDate);
            if (rawData.isEmpty()) {
                log.debug("No chart data found for {} with ID {} in date range {}-{}, returning empty array", entityType.getCode(), entityId, startDate, endDate);
                return new ArrayList<>();
            }

            return generateChartDataWithForwardFill(rawData, startDate, endDate);

        } catch (Exception e) {
            log.warn("Error fetching chart data for {} with ID {}: {}. Returning empty array.", entityType.getCode(), entityId, e.getMessage());
            return new ArrayList<>();
        }
    }

    // ========================================
    // GENERIC METHODS FOR ALL ENTITY TYPES
    // ========================================

    private LocalDate calculateEffectiveStartDate(TimePeriod period, LocalDate endDate, EarliestDateSupplier earliestDateSupplier) throws Exception {
        LocalDate requestedStartDate = period.calculateStartDate(endDate);
        LocalDate earliestAvailableDate = earliestDateSupplier.get();

        if (earliestAvailableDate == null) {
            // No data available, return today's date as start date
            return endDate;
        }

        if (requestedStartDate == null || requestedStartDate.isBefore(earliestAvailableDate)) {
            // Use earliest available date if requested start date is before available data
            return earliestAvailableDate;
        }

        return requestedStartDate;
    }

    private List<ChartData> generateChartDataWithForwardFill(List<ChartData> rawData, LocalDate startDate, LocalDate endDate) {
        if (rawData.isEmpty()) {
            // Return empty data with zeros for the entire range
            return generateEmptyChartData(startDate, endDate);
        }

        // Create a map of existing data by date for quick lookup
        Map<LocalDate, ChartData> dataMap = rawData.stream().collect(Collectors.toMap(ChartData::getDate, data -> data));

        List<ChartData> result = new ArrayList<>();
        long id = 1;

        // Track last known values for forward fill
        BigDecimal lastValue = BigDecimal.ZERO;
        BigDecimal lastInvested = BigDecimal.ZERO;

        // Generate data for each day in the range
        LocalDate currentDate = startDate;
        while (!currentDate.isAfter(endDate)) {
            ChartData existingData = dataMap.get(currentDate);

            if (existingData != null) {
                // Use actual data and update last known values
                lastValue = existingData.getValue();
                lastInvested = existingData.getInvested();

                result.add(ChartData.builder().id(id++).date(currentDate).value(lastValue).invested(lastInvested).build());
            } else {
                // Forward fill with last known values
                result.add(ChartData.builder().id(id++).date(currentDate).value(lastValue).invested(lastInvested).build());
            }

            currentDate = currentDate.plusDays(1);
        }

        return result;
    }

    private List<ChartData> generateEmptyChartData(LocalDate startDate, LocalDate endDate) {
        List<ChartData> result = new ArrayList<>();
        long id = 1;

        LocalDate currentDate = startDate;
        while (!currentDate.isAfter(endDate)) {
            result.add(ChartData.builder().id(id++).date(currentDate).value(BigDecimal.ZERO).invested(BigDecimal.ZERO).build());

            currentDate = currentDate.plusDays(1);
        }

        return result;
    }

    public enum TimePeriod {
        ONE_MONTH("1M", 1, ChronoUnit.MONTHS), THREE_MONTHS("3M", 3, ChronoUnit.MONTHS), SIX_MONTHS("6M", 6, ChronoUnit.MONTHS), ONE_YEAR("1Y", 1, ChronoUnit.YEARS), TWO_YEARS("2Y", 2, ChronoUnit.YEARS), FIVE_YEARS("5Y", 5, ChronoUnit.YEARS), ALL("ALL", 0, null);

        private final String code;
        private final int amount;
        private final ChronoUnit unit;

        TimePeriod(String code, int amount, ChronoUnit unit) {
            this.code = code;
            this.amount = amount;
            this.unit = unit;
        }

        public static TimePeriod fromCode(String code) {
            for (TimePeriod period : values()) {
                if (period.code.equalsIgnoreCase(code)) {
                    return period;
                }
            }
            throw new IllegalArgumentException("Invalid time period: " + code);
        }

        public LocalDate calculateStartDate(LocalDate endDate) {
            if (this == ALL) {
                return null; // Will be handled by earliest available date
            }
            return endDate.minus(amount, unit);
        }
    }

    @FunctionalInterface
    private interface EarliestDateSupplier {
        LocalDate get() throws Exception;
    }
}
