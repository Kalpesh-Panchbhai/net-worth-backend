package com.networth.networthbackend.chartdata;

import com.networth.networthbackend.db.Database;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class ChartDataRepository {

    // ========================================
    // GENERIC METHODS FOR ALL ENTITY TYPES
    // ========================================

    /**
     * Generic method to get chart data for any entity type
     * Returns empty list if entity doesn't exist or has no data
     */
    public List<ChartData> getChartData(EntityType entityType, long entityId, LocalDate startDate, LocalDate endDate) throws Exception {
        String sql = ChartDataQueryBuilder.buildChartDataQuery(entityType);

        try (var connection = Database.getConnection(); var statement = connection.prepareStatement(sql)) {

            statement.setLong(1, entityId);
            statement.setDate(2, Date.valueOf(startDate));
            statement.setDate(3, Date.valueOf(endDate));

            log.debug("Executing chart data query for {}: entityId={}, startDate={}, endDate={}", entityType.getCode(), entityId, startDate, endDate);

            return executeChartDataQuery(statement);

        } catch (Exception e) {
            log.warn("Error executing chart data query for {} with ID {}: {}. Returning empty list.", entityType.getCode(), entityId, e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Generic method to get earliest date for any entity type
     * Returns null if entity doesn't exist or has no data
     */
    public LocalDate getEarliestDate(EntityType entityType, long entityId) throws Exception {
        String sql = ChartDataQueryBuilder.buildEarliestDateQuery(entityType);

        try (var connection = Database.getConnection(); var statement = connection.prepareStatement(sql)) {

            statement.setLong(1, entityId);

            log.debug("Executing earliest date query for {}: entityId={}", entityType.getCode(), entityId);

            try (var resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    Date earliestDate = resultSet.getDate("earliest_date");
                    return earliestDate != null ? earliestDate.toLocalDate() : null;
                }
                return null;
            }

        } catch (Exception e) {
            log.warn("Error executing earliest date query for {} with ID {}: {}. Returning null.", entityType.getCode(), entityId, e.getMessage());
            return null;
        }
    }

    /**
     * Per-member-account daily series for a watchlist, in one query.
     * <p>
     * A multi-currency watchlist must be converted account-by-account before its accounts are
     * summed, which used to mean one query (and one fresh connection) per member account. This
     * returns every member's series at once, already carrying the account's native currency, so
     * the aggregation costs a single round trip regardless of how many accounts are linked.
     * <p>
     * Rows are ordered by (account_id, date) so callers can forward-fill in a single pass.
     */
    public List<AccountSeriesRow> getWatchlistSeriesByAccount(long watchlistId, LocalDate startDate, LocalDate endDate) {
        String sql = """
                SELECT ahvd.account_id, a.currency, ahvd.date,
                       SUM(ahvd.value) AS value, SUM(ahvd.invested) AS invested
                FROM account_holding_values_daily ahvd
                INNER JOIN watchlist_accounts wa ON wa.account_id = ahvd.account_id
                INNER JOIN accounts a ON a.id = ahvd.account_id
                WHERE wa.watchlist_id = ? AND ahvd.date >= ? AND ahvd.date <= ?
                GROUP BY ahvd.account_id, a.currency, ahvd.date
                ORDER BY ahvd.account_id, ahvd.date ASC
                """;
        List<AccountSeriesRow> rows = new ArrayList<>();
        try (var connection = Database.getConnection(); var statement = connection.prepareStatement(sql)) {
            statement.setLong(1, watchlistId);
            statement.setDate(2, Date.valueOf(startDate));
            statement.setDate(3, Date.valueOf(endDate));
            try (var rs = statement.executeQuery()) {
                while (rs.next()) {
                    rows.add(new AccountSeriesRow(
                            rs.getLong("account_id"),
                            rs.getString("currency"),
                            rs.getDate("date").toLocalDate(),
                            nz(rs.getBigDecimal("value")),
                            nz(rs.getBigDecimal("invested"))));
                }
            }
        } catch (Exception e) {
            log.warn("Error loading watchlist {} per-account series [{}..{}]: {}. Returning empty list.",
                    watchlistId, startDate, endDate, e.getMessage());
            return new ArrayList<>();
        }
        return rows;
    }

    /** One member account's totals on one date, in the account's native currency. */
    public record AccountSeriesRow(long accountId, String currency, LocalDate date, BigDecimal value, BigDecimal invested) {
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private List<ChartData> executeChartDataQuery(PreparedStatement statement) throws Exception {
        List<ChartData> chartDataList = new ArrayList<>();

        try (var resultSet = statement.executeQuery()) {
            long id = 1;
            while (resultSet.next()) {
                LocalDate date = resultSet.getDate("date").toLocalDate();
                BigDecimal value = resultSet.getBigDecimal("value");
                BigDecimal invested = resultSet.getBigDecimal("invested");

                ChartData chartData = ChartData.builder().id(id++).date(date).value(value != null ? value : BigDecimal.ZERO).invested(invested != null ? invested : BigDecimal.ZERO).build();

                chartDataList.add(chartData);
            }
        }

        return chartDataList;
    }
}
