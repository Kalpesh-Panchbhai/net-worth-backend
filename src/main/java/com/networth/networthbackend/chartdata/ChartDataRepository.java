package com.networth.networthbackend.chartdata;

import com.networth.networthbackend.common.Constants;
import com.networth.networthbackend.db.Database;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class ChartDataRepository {

    public List<ChartDataPoint> getChartDataForHolding(Long holdingId, LocalDate startDate) throws Exception {
        try (Connection connection = Database.getConnection()) {
            PreparedStatement statement = connection.prepareStatement(Constants.DB.ChartData.SQL_CHART_DATA_FOR_HOLDING);
            statement.setLong(1, holdingId); // holding_info WHERE clause
            statement.setLong(2, holdingId); // earliest_txn_date WHERE clause
            statement.setDate(3, Date.valueOf(startDate)); // effective_dates GREATEST calculation
            statement.setLong(4, holdingId); // all_dates transactions WHERE clause
            statement.setLong(5, holdingId); // invested_values_agg WHERE clause
            
            ResultSet resultSet = statement.executeQuery();
            List<ChartDataPoint> chartData = new ArrayList<>();
            
            while (resultSet.next()) {
                ChartDataPoint point = new ChartDataPoint();
                point.setId(resultSet.getLong("id"));
                point.setDate(resultSet.getDate("date").toLocalDate());
                point.setCurrentDayValue(resultSet.getBigDecimal("currentDayValue"));
                point.setInvested(resultSet.getBigDecimal("invested"));
                chartData.add(point);
            }
            
            return chartData;
        }
    }

    public List<ChartDataPoint> getChartDataForAccount(Long accountId, LocalDate startDate) throws Exception {
        try (Connection connection = Database.getConnection()) {
            PreparedStatement statement = connection.prepareStatement(Constants.DB.ChartData.SQL_CHART_DATA_FOR_ACCOUNT);
            statement.setLong(1, accountId); // account_info WHERE clause
            statement.setLong(2, accountId); // earliest_txn_date WHERE clause
            statement.setDate(3, Date.valueOf(startDate)); // effective_dates GREATEST calculation
            statement.setLong(4, accountId); // all_dates transactions WHERE clause
            statement.setLong(5, accountId); // invested_values_agg WHERE clause
            
            ResultSet resultSet = statement.executeQuery();
            List<ChartDataPoint> chartData = new ArrayList<>();
            
            while (resultSet.next()) {
                ChartDataPoint point = new ChartDataPoint();
                point.setId(resultSet.getLong("id"));
                point.setDate(resultSet.getDate("date").toLocalDate());
                point.setCurrentDayValue(resultSet.getBigDecimal("currentDayValue"));
                point.setInvested(resultSet.getBigDecimal("invested"));
                chartData.add(point);
            }
            
            return chartData;
        }
    }

    public List<ChartDataPoint> getChartDataForWatchlist(Long watchlistId, LocalDate startDate) throws Exception {
        try (Connection connection = Database.getConnection()) {
            PreparedStatement statement = connection.prepareStatement(Constants.DB.ChartData.SQL_CHART_DATA_FOR_WATCHLIST);
            statement.setLong(1, watchlistId); // wl_accounts WHERE clause
            statement.setDate(2, Date.valueOf(startDate)); // effective_dates GREATEST calculation
            
            ResultSet resultSet = statement.executeQuery();
            List<ChartDataPoint> chartData = new ArrayList<>();
            
            while (resultSet.next()) {
                ChartDataPoint point = new ChartDataPoint();
                point.setId(resultSet.getLong("id"));
                point.setDate(resultSet.getDate("date").toLocalDate());
                point.setCurrentDayValue(resultSet.getBigDecimal("currentDayValue"));
                point.setInvested(resultSet.getBigDecimal("invested"));
                chartData.add(point);
            }
            
            return chartData;
        }
    }

    public LocalDate calculateStartDate(String period) {
        LocalDate today = LocalDate.now();
        return switch (period.toUpperCase()) {
            case Constants.ChartData.PERIOD_1M -> today.minusMonths(1);
            case Constants.ChartData.PERIOD_3M -> today.minusMonths(3);
            case Constants.ChartData.PERIOD_6M -> today.minusMonths(6);
            case Constants.ChartData.PERIOD_1Y -> today.minusYears(1);
            case Constants.ChartData.PERIOD_2Y -> today.minusYears(2);
            case Constants.ChartData.PERIOD_5Y -> today.minusYears(5);
            case Constants.ChartData.PERIOD_ALL -> LocalDate.of(2000, 1, 1); // Assuming data doesn't go before year 2000
            default -> today.minusMonths(1); // Default to 1 month
        };
    }

    public boolean isValidPeriod(String period) {
        if (period == null) return false;
        return switch (period.toUpperCase()) {
            case Constants.ChartData.PERIOD_1M,
                 Constants.ChartData.PERIOD_3M,
                 Constants.ChartData.PERIOD_6M,
                 Constants.ChartData.PERIOD_1Y,
                 Constants.ChartData.PERIOD_2Y,
                 Constants.ChartData.PERIOD_5Y,
                 Constants.ChartData.PERIOD_ALL -> true;
            default -> false;
        };
    }

    public boolean isValidChartType(String chartType) {
        if (chartType == null) return false;
        return switch (chartType.toLowerCase()) {
            case Constants.ChartData.CHART_TYPE_HOLDING,
                 Constants.ChartData.CHART_TYPE_ACCOUNT,
                 Constants.ChartData.CHART_TYPE_WATCHLIST -> true;
            default -> false;
        };
    }
}
