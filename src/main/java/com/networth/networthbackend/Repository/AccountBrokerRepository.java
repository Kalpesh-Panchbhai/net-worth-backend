package com.networth.networthbackend.Repository;

import com.networth.networthbackend.entity.AccountBrokerEntity;

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
    public PreparedStatement generateInsertQuery(AccountBrokerEntity data) throws SQLException {
        String insertQuery = "INSERT INTO " + tableName + "(account_id, name, symbol) VALUES (?, ?. ?)";
        PreparedStatement preparedStatement = connection.prepareStatement(insertQuery, PreparedStatement.RETURN_GENERATED_KEYS);
        preparedStatement.setInt(1, data.getAccountId());
        preparedStatement.setString(2, data.getName());
        preparedStatement.setString(3, data.getSymbol());
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
            accountBrokerEntityList.add(accountBrokerEntity);
        }
        return accountBrokerEntityList;
    }
}
