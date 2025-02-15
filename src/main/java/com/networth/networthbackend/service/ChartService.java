package com.networth.networthbackend.service;

import com.networth.networthbackend.Repository.ChartRepository;
import com.networth.networthbackend.entity.AccountEntity;
import com.networth.networthbackend.entity.AccountTransactionEntity;
import com.networth.networthbackend.entity.AccountValueEntity;
import com.networth.networthbackend.entity.ChartEntity;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class ChartService extends Service<ChartEntity> {

    private final Integer userId = 1;

    public ChartService() {
        super(new ChartRepository());
    }

    public void generateData() throws SQLException {
        AccountService accountService = new AccountService();
        List<AccountEntity> accountEntityList = accountService.repository.getBySecondaryField(new LinkedHashMap<>() {{
            put("user_id", userId);
        }});

        for (AccountEntity accountEntity : accountEntityList) {
            if (accountEntity.getType().equals("Broker")) {

            } else {
                generateNonBrokerAccountData(accountEntity.getId());
            }
        }
        accountService.repository.databaseService.disconnect();
    }

    private void generateBrokerAccountData(Integer id) throws SQLException {

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
        deleteData(id);
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

    private void addData(List<ChartEntity> chartEntityList) throws SQLException {
        ChartService chartService = new ChartService();
        for (ChartEntity chartEntity : chartEntityList) {
            chartService.repository.save(chartEntity);
        }
        chartService.repository.databaseService.disconnect();
    }

    private void deleteData(Integer id) throws SQLException {
        ChartService chartService = new ChartService();
        chartService.repository.delete("account_id", id);
        chartService.repository.databaseService.disconnect();
    }
}
