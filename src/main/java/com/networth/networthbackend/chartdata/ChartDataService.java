package com.networth.networthbackend.chartdata;

import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class ChartDataService {

    private final ChartDataRepository repository = new ChartDataRepository();

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
