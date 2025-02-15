package com.networth.networthbackend.service;

import com.amazonaws.services.lambda.runtime.Context;
import com.networth.networthbackend.Repository.ChartRepository;
import com.networth.networthbackend.entity.*;
import com.networth.networthbackend.model.yahooapimodel.YahooAPIResponse;
import com.networth.networthbackend.utility.Utility;

import java.io.IOException;
import java.net.URISyntaxException;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class ChartService extends Service<ChartEntity> {

    private final Integer userId = 1;

    private final String baseURL = "https://query1.finance.yahoo.com/v8/finance/chart/";

    private final RestAPIService restAPIService = new RestAPIService();

    private final Context context;
    Map<LocalDate, Double> currencyDataList = new LinkedHashMap<>();

    AccountValueEntity accountValueEntity = new AccountValueEntity(0.0, 0.0);

    public ChartService(Context context) {
        super(new ChartRepository());
        this.context = context;
    }

    public void generateData() throws SQLException, IOException, URISyntaxException, InterruptedException {
        AccountService accountService = new AccountService();
        List<AccountEntity> accountEntityList = accountService.repository.getBySecondaryField(new LinkedHashMap<>() {{
            put("user_id", userId);
        }});

        if (currencyDataList.isEmpty()) {
            currencyDataList = generateCurrencyData();
        }
        for (AccountEntity accountEntity : accountEntityList) {
            if (accountEntity.getType().equals("Broker")) {
                accountValueEntity = new AccountValueEntity(0.0, 0.0);
                generateBrokerAccountData(accountEntity.getId());
                ChartEntity previousValue = new ChartEntity();
                previousValue.setValue(accountValueEntity.getPreviousValue());
                ChartEntity currentValue = new ChartEntity();
                currentValue.setValue(accountValueEntity.getCurrentValue());
                updateAccountData(accountEntity.getId(), List.of(previousValue, currentValue));
            } else {
                generateNonBrokerAccountData(accountEntity.getId());
            }
        }
        accountService.repository.databaseService.disconnect();
    }

    private void generateBrokerAccountData(Integer id) throws SQLException, IOException, URISyntaxException, InterruptedException {
        AccountBrokerService accountBrokerService = new AccountBrokerService();
        List<AccountBrokerEntity> accountBrokerEntityList = accountBrokerService.repository.getBySecondaryField(new LinkedHashMap<>() {{
            put("account_id", id);
        }});
        for (AccountBrokerEntity accountBrokerEntity : accountBrokerEntityList) {
            List<ChartEntity> chartEntityList = generateData(accountBrokerEntity.getSymbol(), accountBrokerEntity.getId());
            deleteData(accountBrokerEntity.getId(), "account_broker_id");
            addData(chartEntityList);
            updateAccountBrokerData(accountBrokerEntity.getId(), chartEntityList);
        }
        accountBrokerService.repository.databaseService.disconnect();
    }

    public LinkedHashMap<LocalDate, Double> generateCurrencyData() throws IOException, URISyntaxException, InterruptedException {
        String url = generateURL("USDINR=X");
        String currencyResponse = restAPIService.executeAPI(url);
        YahooAPIResponse currencyResponseYahooAPI = new Utility<YahooAPIResponse>().convertStringToObj(currencyResponse, context, YahooAPIResponse.class);
        return convertYahooAPIToChartData(currencyResponseYahooAPI);
    }

    public List<ChartEntity> generateData(String symbol, Integer id) throws IOException, URISyntaxException, InterruptedException, SQLException {
        String url = generateURL(symbol);
        String response = restAPIService.executeAPI(url);

        YahooAPIResponse api = new Utility<YahooAPIResponse>().convertStringToObj(response, context, YahooAPIResponse.class);

        String currency = api.getChart().getResult().get(0).getMeta().getCurrency();
        LinkedHashMap<LocalDate, Double> symbolDataList = convertYahooAPIToChartData(api);

        AccountTransactionService accountTransactionService = new AccountTransactionService();
        List<AccountTransactionEntity> accountTransactionEntityList = accountTransactionService.repository.getBySecondaryField(new LinkedHashMap<>() {{
            put("account_broker_id", id);
        }});
        accountTransactionService.repository.databaseService.disconnect();
        accountTransactionEntityList.sort(Comparator.comparing(AccountTransactionEntity::getTransactionDate));

        List<ChartEntity> chartEntityList = new ArrayList<>();
        if (!accountTransactionEntityList.isEmpty()) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            DateTimeFormatter formatter1 = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            LocalDate startDate = LocalDate.parse(accountTransactionEntityList.get(0).getTransactionDate(), formatter);
            Double validLastSymbolValue = symbolDataList.getOrDefault(startDate, 0.0);
            Double validLastCurrencyValue = currencyDataList.getOrDefault(startDate, 0.0);
            for (int i = 0; i < accountTransactionEntityList.size(); i++) {
                LocalDate currentDate = LocalDate.parse(accountTransactionEntityList.get(i).getTransactionDate(), formatter);
                while (startDate.isBefore(currentDate)) {
                    ChartEntity chartEntity = new ChartEntity();
                    chartEntity.setAccountBrokerId(id);
                    chartEntity.setDate(startDate.format(formatter1));
                    symbolDataList.containsKey(startDate);
                    Double value = symbolDataList.getOrDefault(startDate, validLastSymbolValue) * accountTransactionEntityList.get(i - 1).getValue() * (currency.equals("INR") ? 1 : currencyDataList.getOrDefault(startDate, validLastCurrencyValue));
                    validLastSymbolValue = symbolDataList.getOrDefault(startDate, validLastSymbolValue);
                    validLastCurrencyValue = currencyDataList.getOrDefault(startDate, validLastCurrencyValue);
                    chartEntity.setValue(value);
                    chartEntityList.add(chartEntity);
                    startDate = startDate.plusDays(1);
                }
                ChartEntity chartEntity = new ChartEntity();
                chartEntity.setAccountBrokerId(id);
                chartEntity.setDate(startDate.format(formatter1));
                Double value = symbolDataList.getOrDefault(startDate, validLastSymbolValue) * accountTransactionEntityList.get(i).getValue() * (currency.equals("INR") ? 1 : currencyDataList.getOrDefault(startDate, validLastCurrencyValue));
                validLastSymbolValue = symbolDataList.getOrDefault(startDate, validLastSymbolValue);
                validLastCurrencyValue = currencyDataList.getOrDefault(startDate, validLastCurrencyValue);
                chartEntity.setValue(value);
                chartEntityList.add(chartEntity);
                startDate = startDate.plusDays(1);
            }

            AccountTransactionEntity lastAccountTransactionEntity = accountTransactionEntityList.get(accountTransactionEntityList.size() - 1);
            if (lastAccountTransactionEntity.getValue() != 0) {
                LocalDate now = LocalDateTime.now().toLocalDate().plusDays(1);
                while (!startDate.isEqual(now)) {
                    ChartEntity chartEntity = new ChartEntity();
                    chartEntity.setAccountBrokerId(id);
                    chartEntity.setDate(startDate.format(formatter1));
                    Double value = symbolDataList.getOrDefault(startDate, validLastSymbolValue) * lastAccountTransactionEntity.getValue() * (currency.equals("INR") ? 1 : currencyDataList.getOrDefault(startDate, validLastCurrencyValue));
                    validLastSymbolValue = symbolDataList.getOrDefault(startDate, validLastSymbolValue);
                    validLastCurrencyValue = currencyDataList.getOrDefault(startDate, validLastCurrencyValue);
                    chartEntity.setValue(value);
                    chartEntityList.add(chartEntity);
                    startDate = startDate.plusDays(1);
                }
            }
        }
        return chartEntityList;
    }

    private LinkedHashMap<LocalDate, Double> convertYahooAPIToChartData(YahooAPIResponse yahooAPIResponse) {
        LinkedHashMap<LocalDate, Double> dataList = new LinkedHashMap<>();
        List<String> timestamp = yahooAPIResponse.getChart().getResult().get(0).getTimestamp();
        List<String> close = yahooAPIResponse.getChart().getResult().get(0).getIndicators().getQuote().get(0).getClose();
        LocalDate startDate = LocalDate.now();
        if (!timestamp.isEmpty()) {
            startDate = convertEpochToLocalDate(Long.parseLong(timestamp.get(0)));
        }
        Double lastValidValue = 0.0;
        for (int i = 0; i < timestamp.size(); i++) {
            LocalDate currentDate = convertEpochToLocalDate(Long.parseLong(timestamp.get(i)));
            while (!startDate.isEqual(currentDate)) {
                dataList.put(startDate, lastValidValue);
                startDate = startDate.plusDays(1);
            }
            String value = close.get(i);
            if (value == null) {
                continue;
            }
            dataList.put(convertEpochToLocalDate(Long.parseLong(timestamp.get(i))), Double.parseDouble(value));
            lastValidValue = Double.parseDouble(value);
            startDate = startDate.plusDays(1);
        }
        return dataList;
    }

    private LocalDate convertEpochToLocalDate(long epoch) {
        Instant instant = Instant.ofEpochMilli(epoch * 1000L);
        return instant.atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private String generateURL(String symbol) {
        return baseURL + symbol + "?interval=1d&range=10y";
    }

    private void generateNonBrokerAccountData(Integer id) throws SQLException {
        List<ChartEntity> chartEntityList = new ArrayList<>();
        AccountTransactionService accountTransactionService = new AccountTransactionService();
        List<AccountTransactionEntity> accountTransactionEntityList = accountTransactionService.repository.getBySecondaryField(new LinkedHashMap<>() {{
            put("account_id", id);
        }});

        accountTransactionEntityList.sort(Comparator.comparing(AccountTransactionEntity::getTransactionDate));

        if (!accountTransactionEntityList.isEmpty()) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            LocalDateTime startDate = LocalDateTime.parse(accountTransactionEntityList.get(0).getTransactionDate(), formatter);
            for (int i = 0; i < accountTransactionEntityList.size(); i++) {
                LocalDateTime currentDate = LocalDateTime.parse(accountTransactionEntityList.get(i).getTransactionDate(), formatter);
                while (startDate.isBefore(currentDate)) {
                    ChartEntity chartEntity = new ChartEntity();
                    chartEntity.setAccountId(id);
                    chartEntity.setDate(startDate.format(formatter));
                    chartEntity.setValue(accountTransactionEntityList.get(i - 1).getValue());
                    chartEntityList.add(chartEntity);
                    startDate = startDate.plusDays(1);
                }
                ChartEntity chartEntity = new ChartEntity();
                chartEntity.setAccountId(id);
                chartEntity.setDate(startDate.format(formatter));
                chartEntity.setValue(accountTransactionEntityList.get(i).getValue());
                chartEntityList.add(chartEntity);
                startDate = startDate.plusDays(1);
            }

            AccountTransactionEntity lastAccountTransactionEntity = accountTransactionEntityList.get(accountTransactionEntityList.size() - 1);
            if (lastAccountTransactionEntity.getValue() != 0) {
                LocalDate now = LocalDateTime.now().toLocalDate().plusDays(1);
                while (!startDate.toLocalDate().isEqual(now)) {
                    ChartEntity chartEntity = new ChartEntity();
                    chartEntity.setAccountId(id);
                    chartEntity.setDate(startDate.format(formatter));
                    chartEntity.setValue(lastAccountTransactionEntity.getValue());
                    chartEntityList.add(chartEntity);
                    startDate = startDate.plusDays(1);
                }
            }
        }
        deleteData(id, "account_id");
        addData(chartEntityList);
        updateAccountData(id, chartEntityList);
        accountTransactionService.repository.databaseService.disconnect();
    }

    private void updateAccountData(Integer id, List<ChartEntity> chartEntityList) throws SQLException {
        Double currentValue = !chartEntityList.isEmpty() ? chartEntityList.get(chartEntityList.size() - 1).getValue() : 0.0;
        Double previousValue = chartEntityList.size() >= 2 ? chartEntityList.get(chartEntityList.size() - 2).getValue() : 0.0;

        AccountService accountService = new AccountService();
        Optional<AccountEntity> accountEntity = accountService.repository.getByPrimaryField(id);
        if (accountEntity.isPresent()) {
            accountEntity.ifPresent(accountEntity1 -> {
                accountEntity1.setValue(new AccountValueEntity(currentValue, previousValue));
            });
            accountService.repository.update(accountEntity.get());
        }
        accountService.repository.databaseService.disconnect();
    }

    private void updateAccountBrokerData(Integer id, List<ChartEntity> chartEntityList) throws SQLException {
        Double currentValue = !chartEntityList.isEmpty() ? chartEntityList.get(chartEntityList.size() - 1).getValue() : 0.0;
        Double previousValue = chartEntityList.size() >= 2 ? chartEntityList.get(chartEntityList.size() - 2).getValue() : 0.0;

        accountValueEntity.setCurrentValue(accountValueEntity.getCurrentValue() + currentValue);
        accountValueEntity.setPreviousValue(accountValueEntity.getPreviousValue() + previousValue);
        AccountBrokerService accountBrokerService = new AccountBrokerService();
        Optional<AccountBrokerEntity> accountBrokerEntity = accountBrokerService.repository.getByPrimaryField(id);
        if (accountBrokerEntity.isPresent()) {
            accountBrokerEntity.ifPresent(accountEntity1 -> {
                accountEntity1.setValue(new AccountValueEntity(currentValue, previousValue));
            });
            accountBrokerService.repository.update(accountBrokerEntity.get());
        }
        accountBrokerService.repository.databaseService.disconnect();
    }

    private void addData(List<ChartEntity> chartEntityList) throws SQLException {
        ChartService chartService = new ChartService(context);
        for (ChartEntity chartEntity : chartEntityList) {
            chartService.repository.save(chartEntity);
        }
        chartService.repository.databaseService.disconnect();
    }

    private void deleteData(Integer id, String type) throws SQLException {
        ChartService chartService = new ChartService(context);
        chartService.repository.delete(type, id);
        chartService.repository.databaseService.disconnect();
    }
}
