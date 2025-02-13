package com.networth.networthbackend.Repository;

import com.networth.networthbackend.entity.AccountBrokerEntity;
import com.networth.networthbackend.entity.AccountValueEntity;
import com.networth.networthbackend.entity.ChartEntity;
import com.networth.networthbackend.service.ChartService;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;

public class AccountBrokerRepository extends Repository<AccountBrokerEntity> {

    public AccountBrokerRepository() {
        tableName = "account_broker";
    }

    @Override
    public PreparedStatement prepareInsertQuery(AccountBrokerEntity data) throws SQLException {
        String insertQuery = "INSERT INTO " + tableName + "(account_id, name, symbol) VALUES (?, ?, ?)";
        PreparedStatement preparedStatement = connection.prepareStatement(insertQuery, PreparedStatement.RETURN_GENERATED_KEYS);
        preparedStatement.setInt(1, data.getAccountId());
        preparedStatement.setString(2, data.getName());
        preparedStatement.setString(3, data.getSymbol());
        return preparedStatement;
    }

    @Override
    public PreparedStatement prepareUpdateQuery(AccountBrokerEntity data) throws SQLException {
        String updateQuery = "UPDATE " + tableName + " SET account_id = ?, name = ?, symbol = ? WHERE id = ?";
        PreparedStatement preparedStatement = connection.prepareStatement(updateQuery, PreparedStatement.RETURN_GENERATED_KEYS);
        preparedStatement.setInt(1, data.getAccountId());
        preparedStatement.setString(2, data.getName());
        preparedStatement.setString(3, data.getSymbol());
        preparedStatement.setInt(4, data.getId());
        return preparedStatement;
    }

    @Override
    public List<AccountBrokerEntity> generateEntityData(ResultSet rs) throws SQLException {
        List<AccountBrokerEntity> accountBrokerEntityList = new ArrayList<>();
        while (rs.next()) {
            AccountBrokerEntity accountBrokerEntity = new AccountBrokerEntity();
            accountBrokerEntity.setId(rs.getInt("id"));
            accountBrokerEntity.setAccountId(rs.getInt("account_id"));
            accountBrokerEntity.setName(rs.getString("name"));
            accountBrokerEntity.setSymbol(rs.getString("symbol"));
            accountBrokerEntity.setValue(fetchAccountValueEntity(accountBrokerEntity.getId()));
            accountBrokerEntityList.add(accountBrokerEntity);
        }
        return accountBrokerEntityList;
    }

    private AccountValueEntity fetchAccountValueEntity(Integer id) throws SQLException {
        ChartService chartService = new ChartService();
        List<ChartEntity> chartEntityList = chartService.repository.getBySecondaryField(new LinkedHashMap<>() {{
            put("account_broker_id", id);
        }});
        chartEntityList.sort(Comparator.comparing(ChartEntity::getDate).reversed());
        chartEntityList = chartEntityList.subList(0, Math.min(chartEntityList.size(), 2));
        return new AccountValueEntity(!chartEntityList.isEmpty() ? chartEntityList.get(0).getValue() : 0.0, (!chartEntityList.isEmpty() && chartEntityList.size() >= 2) ? chartEntityList.get(1).getValue() : 0.0);
    }
}
