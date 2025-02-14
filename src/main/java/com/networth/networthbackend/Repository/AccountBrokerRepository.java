package com.networth.networthbackend.Repository;

import com.networth.networthbackend.entity.AccountBrokerEntity;
import com.networth.networthbackend.entity.AccountValueEntity;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class AccountBrokerRepository extends Repository<AccountBrokerEntity> {

    public AccountBrokerRepository() {
        tableName = "account_broker";
    }

    @Override
    public PreparedStatement prepareInsertQuery(AccountBrokerEntity data) throws SQLException {
        String insertQuery = "INSERT INTO " + tableName + "(account_id, name, symbol, current_value, previous_value, current_unit) VALUES (?, ?, ?, ?, ?, ?)";
        PreparedStatement preparedStatement = connection.prepareStatement(insertQuery, PreparedStatement.RETURN_GENERATED_KEYS);
        preparedStatement.setInt(1, data.getAccountId());
        preparedStatement.setString(2, data.getName());
        preparedStatement.setString(3, data.getSymbol());
        preparedStatement.setDouble(4, 0.0);
        preparedStatement.setDouble(5, 0.0);
        preparedStatement.setDouble(6, 0.0);
        return preparedStatement;
    }

    @Override
    public PreparedStatement prepareUpdateQuery(AccountBrokerEntity data) throws SQLException {
        String updateQuery = "UPDATE " + tableName + " SET account_id = ?, name = ?, symbol = ?, current_unit = ?, current_value = ?, previous_value = ? WHERE id = ?";
        PreparedStatement preparedStatement = connection.prepareStatement(updateQuery, PreparedStatement.RETURN_GENERATED_KEYS);
        preparedStatement.setInt(1, data.getAccountId());
        preparedStatement.setString(2, data.getName());
        preparedStatement.setString(3, data.getSymbol());
        preparedStatement.setDouble(4, data.getCurrentUnit());
        preparedStatement.setDouble(5, data.getValue().getCurrentValue());
        preparedStatement.setDouble(6, data.getValue().getPreviousValue());
        preparedStatement.setInt(7, data.getId());
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
            accountBrokerEntity.setCurrentUnit(rs.getDouble("current_unit"));
            accountBrokerEntity.setValue(new AccountValueEntity(rs.getDouble("current_value"), rs.getDouble("previous_value")));
            accountBrokerEntityList.add(accountBrokerEntity);
        }
        return accountBrokerEntityList;
    }
}
