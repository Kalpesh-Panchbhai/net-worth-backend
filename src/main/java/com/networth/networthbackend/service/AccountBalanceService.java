package com.networth.networthbackend.service;

import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.amazonaws.services.lambda.runtime.Context;
import com.networth.networthbackend.Repository.AccountBalanceRepository;
import com.networth.networthbackend.entity.AccountBalanceEntity;
import com.networth.networthbackend.entity.AccountBrokerEntity;
import com.networth.networthbackend.entity.AccountEntity;
import com.networth.networthbackend.entity.WatchEntity;
import com.networth.networthbackend.model.yahooapimodel.ChartData;
import com.networth.networthbackend.model.yahooapimodel.YahooAPIResponse;
import com.networth.networthbackend.utility.Utility;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.IOException;
import java.net.URISyntaxException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class AccountBalanceService {

    private static final RestAPIService restAPIService = new RestAPIService();
    private final UserService userService = new UserService();
    private final AccountService accountService = new AccountService();
    private final AccountBrokerService accountBrokerService = new AccountBrokerService();
    private final WatchService watchService = new WatchService();
    private final AccountBalanceRepository accountBalanceRepository = new AccountBalanceRepository();
    private final List<AccountEntity> accountEntityList = new ArrayList<>();
    private final List<AccountBrokerEntity> accountBrokerEntityList = new ArrayList<>();
    private final List<WatchEntity> watchList = new ArrayList<>();
    private final String baseURL = "https://query1.finance.yahoo.com/v8/finance/chart/";
    Map<String, Map<String, Double>> accountBalanceMap = new HashMap<>();
    private String userId;

    private Context context;

    private void extractDataFromDB() {
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
            accountEntity.setActive(scanResult.getItems().get(i).get("active").getBOOL());
            accountEntity.setAccountType(scanResult.getItems().get(i).get("accountType").getS());
            if (accountEntity.getAccountType().equals("Broker")) {
                getAccountBrokerList(accountEntity.getId());
            }
            accountEntityList.add(accountEntity);
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
            accountBrokerEntity.setCurrentUnit(Double.parseDouble(scanResult.getItems().get(i).get("currentUnit").getN()));
            accountBrokerEntityList.add(accountBrokerEntity);
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
        updateAccountBalance();
    }

    private void updateAccountBalance() {
        for (Map.Entry<String, Map<String, Double>> entry : accountBalanceMap.entrySet()) {
            AccountBalanceEntity accountBalanceEntity = new AccountBalanceEntity();
            accountBalanceEntity.setId(entry.getKey());
            accountBalanceEntity.setBalance(entry.getValue());
            accountBalanceRepository.save(accountBalanceEntity);
        }
    }

    private void generateWatchData() {
        for (WatchEntity watchEntity : watchList) {
            double currentDayValue = 0.0;
            double previousDayValue = 0.0;
            for (String accountId : watchEntity.getAccountList()) {
                currentDayValue += accountBalanceMap.get(accountId).get("currentDayValue");
                previousDayValue += accountBalanceMap.get(accountId).get("previousDayValue");
            }
            accountBalanceMap.put(watchEntity.getName(), Map.of("currentDayValue", currentDayValue, "previousDayValue", previousDayValue));
        }
    }

    private void generateAccountTypeData() {
        Map<String, List<AccountEntity>> accountTypeMap = accountEntityList.stream().filter(AccountEntity::getActive).collect(Collectors.groupingBy(AccountEntity::getAccountType));
        for (Map.Entry<String, List<AccountEntity>> entry : accountTypeMap.entrySet()) {
            double currentDayValue = 0.0;
            double previousDayValue = 0.0;
            for (AccountEntity accountEntity : entry.getValue()) {
                currentDayValue += accountBalanceMap.get(accountEntity.getId()).get("currentDayValue");
                previousDayValue += accountBalanceMap.get(accountEntity.getId()).get("previousDayValue");
            }
            accountBalanceMap.put(entry.getKey(), Map.of("currentDayValue", currentDayValue, "previousDayValue", previousDayValue));
        }
    }

    private void generateAccountChartData() throws IOException, URISyntaxException, InterruptedException {
        for (AccountEntity accountEntity : accountEntityList) {
            if (accountEntity.getAccountType().equals("Broker")) {
                List<AccountBrokerEntity> accountBrokerEntityList = extractAccountBrokerList(accountEntity.getId());
                for (AccountBrokerEntity accountBrokerEntity : accountBrokerEntityList) {
                    if (accountBrokerEntity.getCurrentUnit() != 0.0) {
                        List<ChartData> chartDataList = generateData(accountBrokerEntity.getSymbol());
                        Map<String, Double> accountBalance = getAccountBalance(accountBrokerEntity, chartDataList);
                        accountBalanceMap.put(accountBrokerEntity.getId(), accountBalance);
                    } else {
                        accountBalanceMap.put(accountBrokerEntity.getId(), Map.of("currentDayValue", 0.0, "previousDayValue", 0.0));
                    }
                }
                generateAccountBalanceForOneBroker(accountEntity.getId(), accountBrokerEntityList);
            } else {
                AccountBalanceEntity accountBalanceEntity = accountBalanceRepository.getByPrimaryField(accountEntity.getId(), AccountBalanceEntity.class);
                if (accountBalanceEntity != null) {
                    accountBalanceMap.put(accountEntity.getId(), Map.of("currentDayValue", accountBalanceEntity.getBalance().get("currentDayValue"), "previousDayValue", accountBalanceEntity.getBalance().get("previousDayValue")));
                } else {
                    accountBalanceMap.put(accountEntity.getId(), Map.of("currentDayValue", 0.0, "previousDayValue", 0.0));
                }
            }
        }
    }

    private void generateAccountBalanceForOneBroker(String brokerId, List<AccountBrokerEntity> accountBrokerEntityList) {
        Map<String, Double> accountBalance = new HashMap<>();
        for (AccountBrokerEntity accountBrokerEntity : accountBrokerEntityList) {
            accountBalance.put("currentDayValue", accountBalanceMap.get(accountBrokerEntity.getId()).get("currentDayValue") + (accountBalance.getOrDefault("currentDayValue", 0.0)));
            accountBalance.put("previousDayValue", accountBalanceMap.get(accountBrokerEntity.getId()).get("previousDayValue") + accountBalance.getOrDefault("previousDayValue", 0.0));
        }
        accountBalanceMap.put(brokerId, accountBalance);
    }

    private Map<String, Double> getAccountBalance(AccountBrokerEntity accountBrokerEntity, List<ChartData> chartDataList) {
        Map<String, Double> accountBalance = new HashMap<>();
        if (chartDataList.size() >= 2) {
            accountBalance.put("currentDayValue", chartDataList.get(chartDataList.size() - 1).getValue() * accountBrokerEntity.getCurrentUnit());
            accountBalance.put("previousDayValue", chartDataList.get(chartDataList.size() - 2).getValue() * accountBrokerEntity.getCurrentUnit());
        } else if (chartDataList.size() == 1) {
            accountBalance.put("currentDayValue", chartDataList.get(0).getValue() * accountBrokerEntity.getCurrentUnit());
            accountBalance.put("previousDayValue", 0.0);
        } else {
            accountBalance.put("currentDayValue", 0.0);
            accountBalance.put("previousDayValue", 0.0);
        }
        return accountBalance;
    }

    private List<ChartData> generateData(String symbol) throws IOException, URISyntaxException, InterruptedException {
        String url = generateURL(symbol);
        String response = restAPIService.executeAPI(url);

        YahooAPIResponse api = new Utility<YahooAPIResponse>().convertStringToObj(response, getContext(), YahooAPIResponse.class);
        List<ChartData> currencyDataList = new ArrayList<>();
        if (!api.getChart().getResult().get(0).getMeta().getCurrency().equals("INR")) {
            url = generateURL("USDINR=X");
            String currencyResponse = restAPIService.executeAPI(url);
            YahooAPIResponse currencyResponseYahooAPI = new Utility<YahooAPIResponse>().convertStringToObj(currencyResponse, getContext(), YahooAPIResponse.class);
            currencyDataList = convertYahooAPIToChartData(currencyResponseYahooAPI);
        }
        List<ChartData> chartDataList = convertYahooAPIToChartData(api);
        return convertData(currencyDataList, chartDataList);
    }

    private String generateURL(String symbol) {
        return baseURL + symbol + "?interval=1d&range=10d";
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

    private List<AccountBrokerEntity> extractAccountBrokerList(String accountId) {
        return accountBrokerEntityList.stream().filter(accountBrokerEntity -> accountBrokerEntity.getAccountId().equals(accountId)).collect(Collectors.toList());
    }

    private List<ChartData> convertData(List<ChartData> currencyDataList, List<ChartData> dataList) {
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
}
