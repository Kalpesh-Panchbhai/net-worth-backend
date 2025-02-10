package com.networth.networthbackend.service;

import com.amazonaws.services.lambda.runtime.Context;
import com.networth.networthbackend.Repository.AccountBalanceRepository;
import com.networth.networthbackend.entity.AccountBalanceEntity;
import com.networth.networthbackend.entity.AccountBrokerEntity;
import com.networth.networthbackend.entity.AccountEntity;
import com.networth.networthbackend.entity.UserEntity;
import com.networth.networthbackend.model.BalanceDetailRest;
import com.networth.networthbackend.model.BalanceRest;
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
import java.util.*;
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
    private final AccountBalanceRepository accountBalanceRepository = new AccountBalanceRepository();
    private final List<AccountEntity> accountEntityList = new ArrayList<>();
    private final List<AccountBrokerEntity> accountBrokerEntityList = new ArrayList<>();
    private final String baseURL = "https://query1.finance.yahoo.com/v8/finance/chart/";
    private final Map<String, BalanceRest> accountBalanceMap = new HashMap<>();
    private String userId;

    private Context context;

    private void extractDataFromDB() {
        getUserId();
        getAccountList();
    }

    private void getUserId() {
        Optional<UserEntity> userEntity = userService.repository.getByPrimaryField("Ve43CSFGNFgps5eGk3avtSOClgA3");
        userId = userEntity.map(UserEntity::getId).orElse("");
    }

    private void getAccountList() {
//        ScanResult scanResult = accountService.repository.getBySecondaryField("userId", userId);
//        for (int i = 0; i < scanResult.getItems().size(); i++) {
//            AccountEntity accountEntity = new AccountEntity();
//            accountEntity.setId(scanResult.getItems().get(i).get("id").getS());
//            accountEntity.setActive(scanResult.getItems().get(i).get("active").getBOOL());
//            accountEntity.setAccountType(scanResult.getItems().get(i).get("accountType").getS());
//            if (accountEntity.getAccountType().equals("Broker")) {
//                getAccountBrokerList(accountEntity.getId());
//            }
//            accountEntityList.add(accountEntity);
//        }
    }

    private void getAccountBrokerList(String id) {
//        ScanResult scanResult = accountBrokerService.repository.getBySecondaryField("accountId", id);
//        for (int i = 0; i < scanResult.getItems().size(); i++) {
//            AccountBrokerEntity accountBrokerEntity = new AccountBrokerEntity();
//            accountBrokerEntity.setId(scanResult.getItems().get(i).get("id").getS());
//            accountBrokerEntity.setAccountId(scanResult.getItems().get(i).get("accountId").getS());
//            accountBrokerEntity.setName(scanResult.getItems().get(i).get("name").getS());
//            accountBrokerEntity.setSymbol(scanResult.getItems().get(i).get("symbol").getS());
//            accountBrokerEntity.setCurrentUnit(Double.parseDouble(scanResult.getItems().get(i).get("currentUnit").getN()));
//            accountBrokerEntityList.add(accountBrokerEntity);
//        }
    }

    public void generateChartData() throws IOException, URISyntaxException, InterruptedException {
        extractDataFromDB();
        generateAccountChartData();
        updateAccountBalance();
    }

    private void updateAccountBalance() {
        for (Map.Entry<String, BalanceRest> entry : accountBalanceMap.entrySet()) {
            AccountBalanceEntity accountBalanceEntity = new AccountBalanceEntity();
            accountBalanceEntity.setId(entry.getKey());
            accountBalanceEntity.setBalance(entry.getValue());
//            accountBalanceRepository.save(accountBalanceEntity);
        }
    }

    private void generateAccountChartData() throws IOException, URISyntaxException, InterruptedException {
//        for (AccountEntity accountEntity : accountEntityList) {
//            if (accountEntity.getType().equals("Broker")) {
//                List<AccountBrokerEntity> accountBrokerEntityList = extractAccountBrokerList(accountEntity.getId());
//                for (AccountBrokerEntity accountBrokerEntity : accountBrokerEntityList) {
//                    if (accountBrokerEntity.getCurrentUnit() != 0.0) {
//                        List<ChartData> chartDataList = generateData(accountBrokerEntity.getSymbol());
//                        BalanceRest accountBalance = getAccountBalance(accountBrokerEntity, chartDataList);
//                        accountBalanceMap.put(accountBrokerEntity.getId(), accountBalance);
//                    } else {
//                        accountBalanceMap.put(accountBrokerEntity.getId(), new BalanceRest(new BalanceDetailRest(Utility.convertLocalToUTC(), 0.0), new BalanceDetailRest(Utility.convertLocalToUTC(), 0.0)));
//                    }
//                }
//                generateAccountBalanceForOneBroker(accountEntity.getId(), accountBrokerEntityList);
//            } else {
//                AccountBalanceEntity accountBalanceEntity = accountBalanceRepository.getByPrimaryField(accountEntity.getId(), AccountBalanceEntity.class);
//                if (accountBalanceEntity != null) {
//                    accountBalanceMap.put(accountEntity.getId(), accountBalanceEntity.getBalance());
//                } else {
//                    accountBalanceMap.put(accountEntity.getId(), new BalanceRest(new BalanceDetailRest(Utility.convertLocalToUTC(), 0.0), new BalanceDetailRest(Utility.convertLocalToUTC(), 0.0)));
//                }
//            }
//        }
    }

    private void generateAccountBalanceForOneBroker(String brokerId, List<AccountBrokerEntity> accountBrokerEntityList) {
        BalanceRest balance = new BalanceRest(new BalanceDetailRest(Utility.convertLocalToUTC(), 0.0), new BalanceDetailRest(Utility.convertLocalToUTC(), 0.0));
        for (AccountBrokerEntity accountBrokerEntity : accountBrokerEntityList) {
            balance.getCurrentDayValue().setDateTime(Utility.convertLocalToUTC());
            balance.getCurrentDayValue().setValue(accountBalanceMap.get(accountBrokerEntity.getId()).getCurrentDayValue().getValue() + balance.getCurrentDayValue().getValue());
            balance.getPreviousDayValue().setDateTime(Utility.convertLocalToUTC());
            balance.getPreviousDayValue().setValue(accountBalanceMap.get(accountBrokerEntity.getId()).getPreviousDayValue().getValue() + balance.getPreviousDayValue().getValue());
        }
        accountBalanceMap.put(brokerId, balance);
    }

    public BalanceRest getAccountBalance(AccountBrokerEntity accountBrokerEntity, List<ChartData> chartDataList) {
        BalanceRest balance = new BalanceRest();
        if (chartDataList.size() >= 2) {
//            balance.setCurrentDayValue(new BalanceDetailRest(Utility.convertLocalToUTC(), chartDataList.get(chartDataList.size() - 1).getValue() * accountBrokerEntity.getCurrentUnit()));
//            balance.setPreviousDayValue(new BalanceDetailRest(Utility.convertLocalToUTC(), chartDataList.get(chartDataList.size() - 2).getValue() * accountBrokerEntity.getCurrentUnit()));
        } else if (chartDataList.size() == 1) {
//            balance.setCurrentDayValue(new BalanceDetailRest(Utility.convertLocalToUTC(), chartDataList.get(0).getValue() * accountBrokerEntity.getCurrentUnit()));
            balance.setPreviousDayValue(new BalanceDetailRest(Utility.convertLocalToUTC(), 0.0));
        } else {
            balance.setCurrentDayValue(new BalanceDetailRest(Utility.convertLocalToUTC(), 0.0));
            balance.setPreviousDayValue(new BalanceDetailRest(Utility.convertLocalToUTC(), 0.0));
        }
        return balance;
    }

    public List<ChartData> generateData(String symbol) throws IOException, URISyntaxException, InterruptedException {
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
                if (!data1.isEmpty())
                    chartData.setValue(dataList.get(i).getValue() * data1.get(data1.size() - 1).getValue());
            }
            result.add(chartData);
        }
        return result;
    }
}
