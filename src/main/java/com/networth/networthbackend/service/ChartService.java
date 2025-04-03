package com.networth.networthbackend.service;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
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
import java.util.stream.Collectors;

public class ChartService extends Service<ChartEntity> {

    private final Integer userId = 1;
    private final LocalDate oneWeekAgo = LocalDate.now().minusDays(7);

    private final String baseURL = "https://query1.finance.yahoo.com/v8/finance/chart/";

    private final RestAPIService restAPIService = new RestAPIService();

    private final Context context;
    Map<LocalDate, Double> currencyDataList = new LinkedHashMap<>();

    AccountValueEntity accountValueEntity = new AccountValueEntity(0.0, 0.0);

    public ChartService(Context context) {
        super(new ChartRepository());
        this.context = context;
    }

    @Override
    public APIGatewayProxyResponseEvent getBySecondaryField(APIGatewayProxyRequestEvent apiGatewayRequest, Context context, Class<ChartEntity> entityType) throws SQLException {
        String id = apiGatewayRequest.getQueryStringParameters().get("id");
        String type = apiGatewayRequest.getQueryStringParameters().get("type");
        String range = apiGatewayRequest.getQueryStringParameters().get("range");
        List<ChartEntity> dataList = repository.getBySecondaryField(new LinkedHashMap<>() {{
            put("id", id);
            put("type", type);
            put("range", range);
        }});
        String jsonBody = utilityRest.convertListOfObjToString(dataList, context);
        context.getLogger().log("data fetched successfully from database:::" + jsonBody);
        return createAPIResponse(jsonBody, 200, Utility.createHeaders());
    }

    public void generateData() throws SQLException, IOException, URISyntaxException, InterruptedException {
        AccountService accountService = new AccountService();
        List<AccountEntity> accountEntityList = accountService.repository.getBySecondaryField(new LinkedHashMap<>() {{
            put("user_id", userId);
        }});

        try {
            if (currencyDataList.isEmpty()) {
                currencyDataList = generateCurrencyData();
            }
        } catch (Exception e) {
            context.getLogger().log("Error while fetching currency data with Yahoo API");
        }

        for (AccountEntity accountEntity : accountEntityList) {
            try {
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
            } catch (Exception e) {
                context.getLogger().log("Error while fetching " + accountEntity.getName() + " from Yahoo API");
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
            try {
                List<ChartEntity> chartEntityList = generateData(accountBrokerEntity.getSymbol(), accountBrokerEntity.getId());
                deleteData(accountBrokerEntity.getId(), "account_broker_id");
                addData(chartEntityList);
                updateAccountBrokerData(accountBrokerEntity.getId(), chartEntityList);
            } catch (Exception e) {
                context.getLogger().log("Error while fetching " + accountBrokerEntity.getName() + " from Yahoo API");
            }
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

        InvestService investService = new InvestService();
        List<InvestEntity> investEntityList = investService.repository.getBySecondaryField(new LinkedHashMap<>() {{
            put("account_broker_id", id);
        }});
        investService.repository.databaseService.disconnect();
        investEntityList.sort(Comparator.comparing(InvestEntity::getDate));

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
                    Double value = symbolDataList.getOrDefault(startDate, validLastSymbolValue) * accountTransactionEntityList.get(i - 1).getValue() * (currency.equals("INR") ? 1 : currencyDataList.getOrDefault(startDate, validLastCurrencyValue));
                    validLastSymbolValue = symbolDataList.getOrDefault(startDate, validLastSymbolValue);
                    validLastCurrencyValue = currencyDataList.getOrDefault(startDate, validLastCurrencyValue);
                    if (!startDate.isBefore(oneWeekAgo)) {
                        ChartEntity chartEntity = new ChartEntity();
                        chartEntity.setAccountBrokerId(id);
                        chartEntity.setDate(startDate.format(formatter1));
                        chartEntity.setValue(value);
                        LocalDate finalStartDate = startDate;
                        List<InvestEntity> filteredInvestEntityList = investEntityList.stream().filter(data -> {
                            LocalDate date = LocalDate.parse(data.getDate(), formatter);
                            return date.isBefore(finalStartDate) || date.isEqual(finalStartDate);
                        }).toList();
                        chartEntity.setInvested(!filteredInvestEntityList.isEmpty() ? filteredInvestEntityList.get(filteredInvestEntityList.size() - 1).getValue() : 0.0);
                        chartEntityList.add(chartEntity);
                    }
                    startDate = startDate.plusDays(1);
                }
                Double value = symbolDataList.getOrDefault(startDate, validLastSymbolValue) * accountTransactionEntityList.get(i).getValue() * (currency.equals("INR") ? 1 : currencyDataList.getOrDefault(startDate, validLastCurrencyValue));
                validLastSymbolValue = symbolDataList.getOrDefault(startDate, validLastSymbolValue);
                validLastCurrencyValue = currencyDataList.getOrDefault(startDate, validLastCurrencyValue);
                if (!startDate.isBefore(oneWeekAgo)) {
                    ChartEntity chartEntity = new ChartEntity();
                    chartEntity.setAccountBrokerId(id);
                    chartEntity.setDate(startDate.format(formatter1));
                    chartEntity.setValue(value);
                    LocalDate finalStartDate = startDate;
                    List<InvestEntity> filteredInvestEntityList = investEntityList.stream().filter(data -> {
                        LocalDate date = LocalDate.parse(data.getDate(), formatter);
                        return date.isBefore(finalStartDate) || date.isEqual(finalStartDate);
                    }).toList();
                    chartEntity.setInvested(!filteredInvestEntityList.isEmpty() ? filteredInvestEntityList.get(filteredInvestEntityList.size() - 1).getValue() : 0.0);
                    chartEntityList.add(chartEntity);
                }
                startDate = startDate.plusDays(1);
            }

            AccountTransactionEntity lastAccountTransactionEntity = accountTransactionEntityList.get(accountTransactionEntityList.size() - 1);
            if (lastAccountTransactionEntity.getValue() != 0) {
                LocalDate now = LocalDateTime.now().toLocalDate().plusDays(1);
                while (!startDate.isEqual(now)) {
                    Double value = symbolDataList.getOrDefault(startDate, validLastSymbolValue) * lastAccountTransactionEntity.getValue() * (currency.equals("INR") ? 1 : currencyDataList.getOrDefault(startDate, validLastCurrencyValue));
                    validLastSymbolValue = symbolDataList.getOrDefault(startDate, validLastSymbolValue);
                    validLastCurrencyValue = currencyDataList.getOrDefault(startDate, validLastCurrencyValue);
                    if (!startDate.isBefore(oneWeekAgo)) {
                        ChartEntity chartEntity = new ChartEntity();
                        chartEntity.setAccountBrokerId(id);
                        chartEntity.setDate(startDate.format(formatter1));
                        chartEntity.setValue(value);
                        LocalDate finalStartDate = startDate;
                        List<InvestEntity> filteredInvestEntityList = investEntityList.stream().filter(data -> {
                            LocalDate date = LocalDate.parse(data.getDate(), formatter);
                            return date.isBefore(finalStartDate) || date.isEqual(finalStartDate);
                        }).toList();
                        chartEntity.setInvested(!filteredInvestEntityList.isEmpty() ? filteredInvestEntityList.get(filteredInvestEntityList.size() - 1).getValue() : 0.0);
                        chartEntityList.add(chartEntity);
                    }
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

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        if (!accountTransactionEntityList.isEmpty()) {
            LocalDateTime startDate = LocalDateTime.parse(accountTransactionEntityList.get(0).getTransactionDate(), formatter);
            for (int i = 0; i < accountTransactionEntityList.size(); i++) {
                LocalDateTime currentDate = LocalDateTime.parse(accountTransactionEntityList.get(i).getTransactionDate(), formatter);
                while (startDate.isBefore(currentDate)) {
                    if (!startDate.toLocalDate().isBefore(oneWeekAgo)) {
                        ChartEntity chartEntity = new ChartEntity();
                        chartEntity.setAccountId(id);
                        chartEntity.setDate(startDate.format(formatter));
                        chartEntity.setValue(accountTransactionEntityList.get(i - 1).getValue());
                        chartEntity.setInvested(accountTransactionEntityList.get(i - 1).getValue());
                        chartEntityList.add(chartEntity);
                    }
                    startDate = startDate.plusDays(1);
                }
                if (!startDate.toLocalDate().isBefore(oneWeekAgo)) {
                    ChartEntity chartEntity = new ChartEntity();
                    chartEntity.setAccountId(id);
                    chartEntity.setDate(startDate.format(formatter));
                    chartEntity.setValue(accountTransactionEntityList.get(i).getValue());
                    chartEntity.setInvested(accountTransactionEntityList.get(i).getValue());
                    chartEntityList.add(chartEntity);
                }
                startDate = startDate.plusDays(1);
            }

            AccountTransactionEntity lastAccountTransactionEntity = accountTransactionEntityList.get(accountTransactionEntityList.size() - 1);
            if (lastAccountTransactionEntity.getValue() != 0) {
                LocalDate now = LocalDateTime.now().toLocalDate().plusDays(1);
                while (!startDate.toLocalDate().isEqual(now)) {
                    if (!startDate.toLocalDate().isBefore(oneWeekAgo)) {
                        ChartEntity chartEntity = new ChartEntity();
                        chartEntity.setAccountId(id);
                        chartEntity.setDate(startDate.format(formatter));
                        chartEntity.setValue(lastAccountTransactionEntity.getValue());
                        chartEntity.setInvested(lastAccountTransactionEntity.getValue());
                        chartEntityList.add(chartEntity);
                    }
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
        for (ChartEntity chartEntity : chartEntityList) {
            repository.save(chartEntity);
        }
    }

    private void deleteData(Integer id, String type) throws SQLException {
        repository.delete(type, id);
    }
}
