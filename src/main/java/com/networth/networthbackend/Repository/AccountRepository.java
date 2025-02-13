package com.networth.networthbackend.Repository;

import com.networth.networthbackend.entity.AccountBrokerEntity;
import com.networth.networthbackend.entity.AccountEntity;
import com.networth.networthbackend.entity.AccountValueEntity;
import com.networth.networthbackend.entity.ChartEntity;
import com.networth.networthbackend.service.AccountBrokerService;
import com.networth.networthbackend.service.ChartService;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;

public class AccountRepository extends Repository<AccountEntity> {

    public AccountRepository() {
        tableName = "account";
    }

    @Override
    public PreparedStatement prepareInsertQuery(AccountEntity data) throws SQLException {
        String insertQuery = "INSERT INTO " + tableName + "(name, type, is_active, currency, user_id) VALUES (?, ?, ?, ?, ?)";
        PreparedStatement preparedStatement = connection.prepareStatement(insertQuery, PreparedStatement.RETURN_GENERATED_KEYS);
        preparedStatement.setString(1, data.getName());
        preparedStatement.setString(2, data.getType());
        preparedStatement.setBoolean(3, data.getActive());
        preparedStatement.setString(4, data.getCurrency());
        preparedStatement.setInt(5, data.getUserId());
        return preparedStatement;
    }

    @Override
    public PreparedStatement prepareUpdateQuery(AccountEntity data) throws SQLException {
        String updateQuery = "UPDATE " + tableName + " SET name = ?, type = ?, is_active = ?, currency = ?, user_id = ? WHERE id = ?";
        PreparedStatement preparedStatement = connection.prepareStatement(updateQuery, PreparedStatement.RETURN_GENERATED_KEYS);
        preparedStatement.setString(1, data.getName());
        preparedStatement.setString(2, data.getType());
        preparedStatement.setBoolean(3, data.getActive());
        preparedStatement.setString(4, data.getCurrency());
        preparedStatement.setInt(5, data.getUserId());
        preparedStatement.setInt(6, data.getId());
        return preparedStatement;
    }

    @Override
    public List<AccountEntity> generateEntityData(ResultSet rs) throws SQLException {
        List<AccountEntity> accountEntityList = new ArrayList<>();
        while (rs.next()) {
            AccountEntity accountEntity = new AccountEntity();
            accountEntity.setId(rs.getInt("id"));
            accountEntity.setUserId(rs.getInt("user_id"));
            accountEntity.setName(rs.getString("name"));
            accountEntity.setType(rs.getString("type"));
            accountEntity.setActive(rs.getBoolean("is_active"));
            accountEntity.setCurrency(rs.getString("currency"));
            accountEntity.setValue(fetchAccountValueEntity(accountEntity.getId(), accountEntity.getType()));
            accountEntityList.add(accountEntity);
        }
        return accountEntityList;
    }

    private AccountValueEntity fetchAccountValueEntity(Integer id, String type) {
        ChartService chartService = new ChartService();
        if (type.equals("Broker")) {
            AccountValueEntity accountValueEntity = new AccountValueEntity(0.0, 0.0);
            AccountBrokerService accountBrokerService = new AccountBrokerService();
            List<AccountBrokerEntity> accountBrokerEntityList = accountBrokerService.repository.getBySecondaryField(new LinkedHashMap<>() {{
                put("account_id", id);
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
                return accountValueEntity;
            } else {
                return accountValueEntity;
            }
        } else {
            List<ChartEntity> chartEntityList = chartService.repository.getBySecondaryField(new LinkedHashMap<>() {{
                put("account_id", id);
            }});
            chartEntityList.sort(Comparator.comparing(ChartEntity::getDate).reversed());
            chartEntityList = chartEntityList.subList(0, Math.min(chartEntityList.size(), 2));
            return new AccountValueEntity(!chartEntityList.isEmpty() ? chartEntityList.get(0).getValue() : 0.0, (!chartEntityList.isEmpty() && chartEntityList.size() >= 2) ? chartEntityList.get(1).getValue() : 0.0);
        }
    }
}
