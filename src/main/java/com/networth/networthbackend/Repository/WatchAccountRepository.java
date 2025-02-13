package com.networth.networthbackend.Repository;

import com.networth.networthbackend.entity.*;
import com.networth.networthbackend.service.*;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class WatchAccountRepository extends Repository<WatchAccountEntity> {

    public WatchAccountRepository() {
        tableName = "watch_account";
    }

    @Override
    public PreparedStatement prepareInsertQuery(WatchAccountEntity data) throws SQLException {
        String insertQuery = "INSERT INTO " + tableName + "(account_id, watch_id) VALUES (?, ?)";
        PreparedStatement preparedStatement = connection.prepareStatement(insertQuery, PreparedStatement.RETURN_GENERATED_KEYS);
        preparedStatement.setInt(1, data.getAccountId());
        preparedStatement.setInt(2, data.getWatchId());
        return preparedStatement;
    }

    @Override
    public PreparedStatement prepareUpdateQuery(WatchAccountEntity data) throws SQLException {
        String updateQuery = "UPDATE " + tableName + " SET account_id = ?, watch_id = ? WHERE id = ?";
        PreparedStatement preparedStatement = connection.prepareStatement(updateQuery, PreparedStatement.RETURN_GENERATED_KEYS);
        preparedStatement.setInt(1, data.getAccountId());
        preparedStatement.setInt(2, data.getWatchId());
        preparedStatement.setInt(3, data.getId());
        return preparedStatement;
    }

    @Override
    public List<WatchAccountEntity> generateEntityData(ResultSet rs) throws SQLException {
        List<WatchAccountEntity> watchEntityList = new ArrayList<>();
        while (rs.next()) {
            WatchAccountEntity watchAccountEntity = new WatchAccountEntity();
            watchAccountEntity.setId(rs.getInt("id"));
            watchAccountEntity.setAccountId(rs.getInt("account_id"));
            watchAccountEntity.setWatchId(rs.getInt("watch_id"));
            watchEntityList.add(watchAccountEntity);
        }
        boolean isListForSameAccount = watchEntityList.stream().map(WatchAccountEntity::getAccountId).distinct().count() == 1;
        if (isListForSameAccount) {
            watchEntityList.forEach(watchAccountEntity -> {
                WatchEntity watchEntity = new WatchEntity();
                watchEntity.setId(watchAccountEntity.getId());
                try {
                    fetchAccountValueForWatchEntity(watchEntity);
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
                watchAccountEntity.setWatch(watchEntity);
            });
        } else {
            watchEntityList.forEach(watchAccountEntity -> {
                AccountEntity accountEntity = new AccountEntity();
                accountEntity.setId(watchAccountEntity.getId());
                try {
                    fetchAccountValueForAccountEntity(accountEntity);
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
                watchAccountEntity.setAccount(accountEntity);
            });
        }
        return watchEntityList;
    }

    private void fetchAccountValueForWatchEntity(WatchEntity watchEntity) throws SQLException {
        WatchAccountService watchAccountService = new WatchAccountService();
        AccountService accountService = new AccountService();
        AccountValueEntity accountValueEntity = new AccountValueEntity(0.0, 0.0);
        List<WatchAccountEntity> watchAccountEntityList = watchAccountService.repository.getBySecondaryField(new LinkedHashMap<>() {{
            put("watch_id", watchEntity.getId());
        }});
        for (WatchAccountEntity watchAccountEntity : watchAccountEntityList) {
            Optional<AccountEntity> accountEntity = accountService.repository.getByPrimaryField(watchAccountEntity.getAccountId());
            if (accountEntity.isPresent()) {
                accountValueEntity.setCurrentBalance(accountValueEntity.getCurrentBalance() + accountEntity.get().getValue().getCurrentBalance());
                accountValueEntity.setPreviousBalance(accountValueEntity.getPreviousBalance() + accountEntity.get().getValue().getPreviousBalance());
            }
        }
        watchEntity.setTotalAccount(watchAccountEntityList.size());
        watchEntity.setValue(accountValueEntity);

        WatchService watchService = new WatchService();
        Optional<WatchEntity> fetchedWatchEntity = watchService.repository.getByPrimaryField(watchEntity.getId());
        if (fetchedWatchEntity.isPresent()) {
            watchEntity.setName(fetchedWatchEntity.get().getName());
            watchEntity.setUserId(fetchedWatchEntity.get().getUserId());
        }
    }

    private void fetchAccountValueForAccountEntity(AccountEntity accountEntity) throws SQLException {
        AccountService accountService = new AccountService();
        Optional<AccountEntity> fetchedAccountEntity = accountService.repository.getByPrimaryField(accountEntity.getId());
        String type = "";
        if (fetchedAccountEntity.isPresent()) {
            type = fetchedAccountEntity.get().getType();
            accountEntity.setName(fetchedAccountEntity.get().getName());
            accountEntity.setUserId(fetchedAccountEntity.get().getUserId());
            accountEntity.setType(type);
            accountEntity.setCurrency(fetchedAccountEntity.get().getCurrency());
            accountEntity.setActive(fetchedAccountEntity.get().getActive());
        }
        ChartService chartService = new ChartService();
        if (type.equals("Broker")) {
            AccountValueEntity accountValueEntity = new AccountValueEntity(0.0, 0.0);
            AccountBrokerService accountBrokerService = new AccountBrokerService();
            List<AccountBrokerEntity> accountBrokerEntityList = accountBrokerService.repository.getBySecondaryField(new LinkedHashMap<>() {{
                put("account_id", accountEntity.getId());
            }});
            if (!accountBrokerEntityList.isEmpty()) {
                Double currentBalance = 0.0;
                Double previousBalance = 0.0;
                for (AccountBrokerEntity accountBrokerEntity : accountBrokerEntityList) {
                    List<ChartEntity> chartEntityList = chartService.repository.getBySecondaryField(new LinkedHashMap<>() {{
                        put("account_broker_id", accountBrokerEntity.getId());
                    }});
                    chartEntityList.sort(Comparator.comparing(ChartEntity::getDate).reversed());
                    chartEntityList = chartEntityList.subList(0, Math.min(chartEntityList.size(), 2));
                    currentBalance += !chartEntityList.isEmpty() ? chartEntityList.get(0).getValue() : 0.0;
                    previousBalance += (!chartEntityList.isEmpty() && chartEntityList.size() >= 2) ? chartEntityList.get(1).getValue() : 0.0;
                }
                accountValueEntity.setCurrentBalance(currentBalance);
                accountValueEntity.setPreviousBalance(previousBalance);
                accountEntity.setValue(accountValueEntity);
            } else {
                accountEntity.setValue(accountValueEntity);
            }
        } else {
            List<ChartEntity> chartEntityList = chartService.repository.getBySecondaryField(new LinkedHashMap<>() {{
                put("account_id", accountEntity.getId());
            }});
            chartEntityList.sort(Comparator.comparing(ChartEntity::getDate).reversed());
            chartEntityList = chartEntityList.subList(0, Math.min(chartEntityList.size(), 2));
            accountEntity.setValue(new AccountValueEntity(!chartEntityList.isEmpty() ? chartEntityList.get(0).getValue() : 0.0, (!chartEntityList.isEmpty() && chartEntityList.size() >= 2) ? chartEntityList.get(1).getValue() : 0.0));
        }
    }
}
