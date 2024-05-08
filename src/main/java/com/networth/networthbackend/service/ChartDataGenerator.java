package com.networth.networthbackend.service;

import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.amazonaws.services.lambda.runtime.ClientContext;
import com.amazonaws.services.lambda.runtime.CognitoIdentity;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.networth.networthbackend.entity.*;
import com.networth.networthbackend.model.yahooapimodel.ChartData;
import com.networth.networthbackend.model.yahooapimodel.YahooAPIResponse;
import com.networth.networthbackend.utility.Utility;

import java.io.IOException;
import java.net.URISyntaxException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

public class ChartDataGenerator {

    private static final RestAPIService restAPIService = new RestAPIService();
    private final UserService userService = new UserService();
    private final AccountService accountService = new AccountService();
    private final AccountBrokerService accountBrokerService = new AccountBrokerService();
    private final AccountTransactionService accountTransactionService = new AccountTransactionService();
    private final WatchService watchService = new WatchService();
    private final ChartDataService chartDataService = new ChartDataService();
    private final List<AccountEntity> accountList = new ArrayList<>();
    private final List<AccountBrokerEntity> accountBrokerEntityList = new ArrayList<>();
    private final List<AccountTransactionEntity> accountTransactionList = new ArrayList<>();
    private final List<WatchEntity> watchList = new ArrayList<>();
    private final String baseURL = "https://query1.finance.yahoo.com/v8/finance/chart/";
    private final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z");
    Map<String, List<ChartData>> chartDataMap = new HashMap<>();
    private String userId;


    private static Context getContext() {
        return new Context() {
            @Override
            public String getAwsRequestId() {
                return "";
            }

            @Override
            public String getLogGroupName() {
                return "";
            }

            @Override
            public String getLogStreamName() {
                return "";
            }

            @Override
            public String getFunctionName() {
                return "";
            }

            @Override
            public String getFunctionVersion() {
                return "";
            }

            @Override
            public String getInvokedFunctionArn() {
                return "";
            }

            @Override
            public CognitoIdentity getIdentity() {
                return null;
            }

            @Override
            public ClientContext getClientContext() {
                return null;
            }

            @Override
            public int getRemainingTimeInMillis() {
                return 0;
            }

            @Override
            public int getMemoryLimitInMB() {
                return 0;
            }

            @Override
            public LambdaLogger getLogger() {
                return null;
            }
        };
    }

    private static List<ChartData> convertData(List<ChartData> currencyDataList, List<ChartData> dataList) {
        List<ChartData> result = new ArrayList<>();
        for (int i = 0; i < dataList.size(); i++) {
            ChartData chartData = new ChartData();
            chartData.setLocalDate(dataList.get(i).getLocalDate());
            if (currencyDataList.isEmpty()) {
                chartData.setValue(dataList.get(i).getValue());
            } else {
                List<ChartData> data1 = currencyDataList.stream().filter(data -> (data.getLocalDate().isBefore(chartData.getLocalDate()) || data.getLocalDate().isEqual(chartData.getLocalDate()))).collect(Collectors.toList());
                chartData.setValue(dataList.get(i).getValue() * data1.get(data1.size() - 1).getValue());
            }
            result.add(chartData);
        }
        return result;
    }

    public void extractDataFromDB() {
        getUserId();
        getAccountList();
        getWatchList();
    }

    private void getUserId() {
        ScanResult scanResult = userService.repository.getBySecondaryField("userId", "Ve43CSFGNFgps5eGk3avtSOClgA3");
        userId = scanResult.getItems().get(0).get("id").getS();
    }

    private void getAccountList() {
        ScanResult scanResult = accountService.repository.getBySecondaryField("userId", userId);
        for (int i = 0; i < scanResult.getItems().size(); i++) {
            AccountEntity accountEntity = new AccountEntity();
            accountEntity.setId(scanResult.getItems().get(i).get("id").getS());
            accountEntity.setUserId(scanResult.getItems().get(i).get("userId").getS());
            accountEntity.setAccountName(scanResult.getItems().get(i).get("accountName").getS());
            accountEntity.setAccountType(scanResult.getItems().get(i).get("accountType").getS());
            if (scanResult.getItems().get(i).get("loanType") != null) {
                accountEntity.setLoanType(scanResult.getItems().get(i).get("loanType").getS());
            }
            accountEntity.setCurrency(scanResult.getItems().get(i).get("currency").getS());
            accountEntity.setActive(scanResult.getItems().get(i).get("active").getBOOL());
            if (accountEntity.getAccountType().equals("Broker")) {
                getAccountBrokerList(accountEntity.getId());
            } else {
                getAccountTransactionList(accountEntity.getId());
            }
            accountList.add(accountEntity);
        }
    }

    private void getAccountBrokerList(String id) {
        ScanResult scanResult = accountBrokerService.repository.getBySecondaryField("accountId", id);
        for (int i = 0; i < scanResult.getItems().size(); i++) {
            AccountBrokerEntity accountBrokerEntity = new AccountBrokerEntity();
            accountBrokerEntity.setId(scanResult.getItems().get(i).get("id").getS());
            accountBrokerEntity.setAccountId(scanResult.getItems().get(i).get("accountId").getS());
            accountBrokerEntity.setName(scanResult.getItems().get(i).get("name").getS());
            accountBrokerEntity.setSymbol(scanResult.getItems().get(i).get("symbol").getS());
            getAccountTransactionList(accountBrokerEntity.getId());
            accountBrokerEntityList.add(accountBrokerEntity);
        }
    }

    private void getAccountTransactionList(String id) {
        ScanResult scanResult = accountTransactionService.repository.getBySecondaryField("accountId", id);
        for (int i = 0; i < scanResult.getItems().size(); i++) {
            AccountTransactionEntity accountTransactionEntity = new AccountTransactionEntity();
            accountTransactionEntity.setId(scanResult.getItems().get(i).get("id").getS());
            accountTransactionEntity.setAccountId(scanResult.getItems().get(i).get("accountId").getS());
            accountTransactionEntity.setTimestamp(scanResult.getItems().get(i).get("timestamp").getS());
            accountTransactionEntity.setCurrentBalance(Double.parseDouble(scanResult.getItems().get(i).get("currentBalance").getN()));
            accountTransactionEntity.setPaid(scanResult.getItems().get(i).get("paid").getBOOL());
            accountTransactionList.add(accountTransactionEntity);
        }
    }

    private void getWatchList() {
        ScanResult scanResult = watchService.repository.getBySecondaryField("userId", userId);
        for (int i = 0; i < scanResult.getItems().size(); i++) {
            WatchEntity watchEntity = new WatchEntity();
            watchEntity.setId(scanResult.getItems().get(i).get("id").getS());
            watchEntity.setUserId(scanResult.getItems().get(i).get("userId").getS());
            watchEntity.setName(scanResult.getItems().get(i).get("name").getS());
            List<String> accountList = scanResult.getItems().get(i).get("accountList").getL().stream().map(AttributeValue::getS).collect(Collectors.toList());
            watchEntity.setAccountList(accountList);
            watchList.add(watchEntity);
        }
    }

    public void generateChartData() throws IOException, URISyntaxException, InterruptedException {
        extractDataFromDB();
        generateAccountChartData();
        generateAccountTypeData();
        generateWatchData();

        deleteExistingChart();
        for (Map.Entry<String, List<ChartData>> entry : chartDataMap.entrySet()) {
            for (ChartData chartData : entry.getValue()) {
                ChartDataEntity chartDataEntity = new ChartDataEntity(null, userId, entry.getKey(), chartData.getLocalDate().toString(), chartData.getValue());
                chartDataService.repository.save(chartDataEntity);
            }
        }
    }

    private void deleteExistingChart() {
        chartDataService.repository.getBySecondaryField("userId", userId).getItems().forEach(item -> {
            chartDataService.repository.delete(new ChartDataEntity(item.get("id").getS(), "", "", "", 0.0));
        });
    }

    private void generateAccountTypeData() {
        Map<String, List<AccountEntity>> accountListByAccountType = accountList.stream().collect(Collectors.groupingBy(AccountEntity::getAccountType));
        for (Map.Entry<String, List<AccountEntity>> entry : accountListByAccountType.entrySet()) {
            chartDataMap.put(entry.getKey(), fetchChartDataList(entry.getValue()));
        }
    }

    private List<ChartData> fetchChartDataList(List<AccountEntity> accountList) {
        Map<String, List<ChartData>> chartDataMapForOneBroker = new HashMap<>();
        for (AccountEntity accountEntity : accountList) {
            chartDataMapForOneBroker.put(accountEntity.getId(), chartDataMap.get(accountEntity.getId()));
        }
        return generateChartDataForMultipleAccounts(chartDataMapForOneBroker);
    }

    private void generateWatchData() {
        List<WatchEntity> watchEntityList = watchList;
        for (WatchEntity watchEntity : watchEntityList) {
            chartDataMap.put(watchEntity.getId(), fetchChartDataListForWatch(watchEntity.getAccountList()));
        }
    }

    private List<ChartData> fetchChartDataListForWatch(List<String> accountList) {
        Map<String, List<ChartData>> chartDataMapForOneBroker = new HashMap<>();
        for (String account : accountList) {
            chartDataMapForOneBroker.put(account, chartDataMap.get(account));
        }
        return generateChartDataForMultipleAccounts(chartDataMapForOneBroker);
    }

    private void generateAccountChartData() throws IOException, URISyntaxException, InterruptedException {
        Map<String, List<AccountEntity>> accountListByAccountType = accountList.stream().collect(Collectors.groupingBy(AccountEntity::getAccountType));
        for (Map.Entry<String, List<AccountEntity>> entry : accountListByAccountType.entrySet()) {
            for (AccountEntity accountEntity : entry.getValue()) {
                if (entry.getKey().equals("Broker")) {
                    generateDataForBrokerAccount(accountEntity);
                } else {
                    generateDataForNonBrokerAccount(accountEntity);
                }
            }
        }
    }

    private void generateDataForBrokerAccount(AccountEntity accountEntity) throws IOException, URISyntaxException, InterruptedException {
        List<AccountBrokerEntity> accountBrokerList = extractAccountBrokerList(accountEntity.getId());
        for (AccountBrokerEntity accountBrokerEntity : accountBrokerList) {
            generateDataForOneAccountInBroker(accountBrokerEntity);
        }
        chartDataMap.put(accountEntity.getId(), generateDataForAllAccountInABroker(accountBrokerList));
    }

    private List<ChartData> generateDataForAllAccountInABroker(List<AccountBrokerEntity> accountBrokerList) {
        Map<String, List<ChartData>> chartDataMapForOneBroker = new HashMap<>();
        for (AccountBrokerEntity accountBrokerEntity : accountBrokerList) {
            chartDataMapForOneBroker.put(accountBrokerEntity.getId(), chartDataMap.get(accountBrokerEntity.getId()));
        }
        return generateChartDataForMultipleAccounts(chartDataMapForOneBroker);
    }

    private List<ChartData> generateChartDataForMultipleAccounts(Map<String, List<ChartData>> chartDataMapForOneBroker) {
        List<ChartData> chartDataList = new ArrayList<>();
        LocalDate startDate = getStartDate(chartDataMapForOneBroker);
        LocalDate endDate = getEndDate(chartDataMapForOneBroker);

        while (startDate.isBefore(endDate) || startDate.isEqual(endDate)) {
            double totalValue = 0.0;
            for (Map.Entry<String, List<ChartData>> entry : chartDataMapForOneBroker.entrySet()) {
                LocalDate finalStartDate = startDate;
                List<ChartData> chartData = entry.getValue().stream().filter(data -> data.getLocalDate().isEqual(finalStartDate) || data.getLocalDate().isBefore(finalStartDate)).collect(Collectors.toList());
                if (!chartData.isEmpty()) {
                    totalValue += chartData.get(chartData.size() - 1).getValue();
                }
            }
            chartDataList.add(new ChartData(startDate, totalValue));
            startDate = startDate.plusDays(1);
        }
        return chartDataList;
    }

    private LocalDate getStartDate(Map<String, List<ChartData>> chartDataMap) {
        LocalDate startDate = LocalDate.now();
        for (Map.Entry<String, List<ChartData>> entry : chartDataMap.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                LocalDate date = entry.getValue().get(0).getLocalDate();
                if (date.isBefore(startDate)) {
                    startDate = date;
                }
            }
        }
        return startDate;
    }

    private LocalDate getEndDate(Map<String, List<ChartData>> chartDataMap) {
        LocalDate endDate = LocalDate.now().minusYears(10);
        for (Map.Entry<String, List<ChartData>> entry : chartDataMap.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                LocalDate date = entry.getValue().get(entry.getValue().size() - 1).getLocalDate();
                if (date.isAfter(endDate)) {
                    endDate = date;
                }
            }
        }
        return endDate;
    }

    private void generateDataForOneAccountInBroker(AccountBrokerEntity accountBrokerEntity) throws IOException, URISyntaxException, InterruptedException {
        List<AccountTransactionEntity> accountTransactionList = extractAccountTransactionlist(accountBrokerEntity.getId());
        accountTransactionList.sort(Comparator.comparing(AccountTransactionEntity::getTimestamp));
        List<ChartData> symbolData = generateData(accountBrokerEntity.getSymbol(), LocalDateTime.parse(accountTransactionList.get(0).getTimestamp(), dateTimeFormatter).toLocalDate(), LocalDateTime.parse(accountTransactionList.get(accountTransactionList.size() - 1).getTimestamp(), dateTimeFormatter).toLocalDate(), accountTransactionList.get(accountTransactionList.size() - 1).getCurrentBalance() == 0.0);
        List<ChartData> chartDataList = generateChartDataForOneSymbol(symbolData, accountTransactionList);
        chartDataMap.put(accountBrokerEntity.getId(), chartDataList);
    }

    private List<ChartData> generateChartDataForOneSymbol(List<ChartData> symbolList, List<AccountTransactionEntity> accountTransactionList) {
        List<ChartData> chartDataList = new ArrayList<>();
        symbolList = removeUnnecessaryData(symbolList, accountTransactionList.get(0));
        for (ChartData symbol : symbolList) {
            List<AccountTransactionEntity> accountTransactionEntityList = accountTransactionList.stream().filter(accountTransactionEntity -> symbol.getLocalDate().isEqual(LocalDateTime.parse(accountTransactionEntity.getTimestamp(), dateTimeFormatter).toLocalDate()) || symbol.getLocalDate().isAfter(LocalDateTime.parse(accountTransactionEntity.getTimestamp(), dateTimeFormatter).toLocalDate())).collect(Collectors.toList());
            if (!accountTransactionEntityList.isEmpty()) {
                ChartData chartData = new ChartData();
                chartData.setLocalDate(symbol.getLocalDate());
                AccountTransactionEntity lastTransaction = accountTransactionEntityList.get(accountTransactionEntityList.size() - 1);
                if (lastTransaction.getCurrentBalance() == 0.0) {
                    chartData.setValue(0.0);
                    chartDataList.add(chartData);
                    break;
                } else {
                    chartData.setValue(symbol.getValue() * lastTransaction.getCurrentBalance());
                }
                chartDataList.add(chartData);
            }
        }
        return chartDataList;
    }

    private List<ChartData> removeUnnecessaryData(List<ChartData> symbolList, AccountTransactionEntity accountTransactionEntity) {
        LocalDate firstTransactionDate = LocalDateTime.parse(accountTransactionEntity.getTimestamp(), dateTimeFormatter).toLocalDate();
        List<ChartData> removedSymbolList1 = symbolList.stream().filter(data -> data.getLocalDate().isAfter(firstTransactionDate) || data.getLocalDate().isEqual(firstTransactionDate)).collect(Collectors.toList());
        List<ChartData> removedSymbolList2 = symbolList.stream().filter(data -> data.getLocalDate().isBefore(firstTransactionDate)).collect(Collectors.toList());
        if (!removedSymbolList2.isEmpty()) {
            removedSymbolList1.add(0, removedSymbolList2.get(removedSymbolList2.size() - 1));
        }
        return removedSymbolList1;
    }

    private List<ChartData> generateData(String symbol, LocalDate firstTransactionDate, LocalDate lastTransactionDate, Boolean isAccountClosed) throws IOException, URISyntaxException, InterruptedException {
        String url = generateURL(symbol, firstTransactionDate, lastTransactionDate, isAccountClosed);
        String response = restAPIService.executeAPI(url);

        YahooAPIResponse api = new Utility<YahooAPIResponse>().convertStringToObj(response, getContext(), YahooAPIResponse.class);
        List<ChartData> currencyDataList = new ArrayList<>();
        if (!api.getChart().getResult().get(0).getMeta().getCurrency().equals("INR")) {
            url = generateCurrencyURL(url);
            String currencyResponse = restAPIService.executeAPI(url);
            YahooAPIResponse currencyResponseYahooAPI = new Utility<YahooAPIResponse>().convertStringToObj(currencyResponse, getContext(), YahooAPIResponse.class);
            currencyDataList = convertYahooAPIToChartData(currencyResponseYahooAPI);
        }
        List<ChartData> chartDataList = convertYahooAPIToChartData(api);
        return convertData(currencyDataList, chartDataList);
    }

    private String generateURL(String symbol, LocalDate firstTransactionDate, LocalDate lastTransactionDate, Boolean isAccountClosed) {
        return baseURL + symbol + "?interval=1d&range=" + getRange(firstTransactionDate, lastTransactionDate, isAccountClosed);
    }

    private String generateCurrencyURL(String url) {
        String symbolRange = url.substring(url.lastIndexOf("=") + 1);
        String currencyRange = getNextRange(symbolRange);
        return "https://query1.finance.yahoo.com/v8/finance/chart/USDINR=X?interval=1d&range=" + currencyRange;
    }

    private String getRange(LocalDate firstTransactionDate, LocalDate lastTransactionDate, Boolean isAccountClosed) {
        long daysDifference = 0;
        String range = "1d";
        if (isAccountClosed) {
            daysDifference = ChronoUnit.DAYS.between(firstTransactionDate, lastTransactionDate);
        } else {
            daysDifference = ChronoUnit.DAYS.between(firstTransactionDate, LocalDate.now());
        }
        if (daysDifference <= 1) {
            range = "1d";
        } else if (daysDifference <= 3) {
            range = "5d";
        } else if (daysDifference <= 20) {
            range = "1mo";
        } else if (daysDifference <= 80) {
            range = "3mo";
        } else if (daysDifference <= 170) {
            range = "6mo";
        } else if (daysDifference <= 355) {
            range = "1y";
        } else if (daysDifference <= 720) {
            range = "2y";
        } else if (daysDifference <= 1815) {
            range = "5y";
        } else {
            range = "5y";
        }
        return range;
    }

    private String getNextRange(String range) {
        switch (range) {
            case "1d":
                return "5d";
            case "5d":
                return "1mo";
            case "1mo":
                return "3mo";
            case "3mo":
                return "6mo";
            case "6mo":
                return "1y";
            case "1y":
                return "2y";
            case "2y":
                return "5y";
            default:
                return "10y";
        }
    }

    private List<ChartData> convertYahooAPIToChartData(YahooAPIResponse yahooAPIResponse) {
        List<ChartData> chartDataList = new ArrayList<>();
        List<String> timestamp = yahooAPIResponse.getChart().getResult().get(0).getTimestamp();
        List<String> close = yahooAPIResponse.getChart().getResult().get(0).getIndicators().getQuote().get(0).getClose();
        for (int i = 0; i < timestamp.size(); i++) {
            ChartData chartData = new ChartData();
            chartData.setLocalDate(convertEpochToLocalDate(Long.parseLong(timestamp.get(i))));
            String value = close.get(i);
            if (value == null) {
                continue;
            }
            chartData.setValue(Double.parseDouble(value));
            chartDataList.add(chartData);
        }
        return chartDataList;
    }

    private LocalDate convertEpochToLocalDate(long epoch) {
        Instant instant = Instant.ofEpochMilli(epoch * 1000L);
        return instant.atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private void generateDataForNonBrokerAccount(AccountEntity accountEntity) {
        List<AccountTransactionEntity> accountTransactionList = extractAccountTransactionlist(accountEntity.getId());
        accountTransactionList.sort(Comparator.comparing(AccountTransactionEntity::getTimestamp));
        List<ChartData> chartDataList = new ArrayList<>();
        for (AccountTransactionEntity accountTransactionEntity : accountTransactionList) {
            ChartData chartData = new ChartData();
            chartData.setLocalDate(LocalDateTime.parse(accountTransactionEntity.getTimestamp(), dateTimeFormatter).toLocalDate());
            chartData.setValue(accountTransactionEntity.getCurrentBalance());
            chartDataList.add(chartData);
        }
        chartDataMap.put(accountEntity.getId(), chartDataList);
    }

    private List<AccountBrokerEntity> extractAccountBrokerList(String accountId) {
        return accountBrokerEntityList.stream().filter(accountBrokerEntity -> accountBrokerEntity.getAccountId().equals(accountId)).collect(Collectors.toList());
    }

    private List<AccountTransactionEntity> extractAccountTransactionlist(String accountId) {
        return accountTransactionList.stream().filter(accountTransactionEntity -> accountTransactionEntity.getAccountId().equals(accountId)).collect(Collectors.toList());
    }
}
