package com.networth.networthbackend.Repository;

import com.networth.networthbackend.entity.AccountTransactionEntity;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class AccountTransactionRepository extends Repository<AccountTransactionEntity> {

    public AccountTransactionRepository() {
        tableName = "account_transaction";
    }

    @Override
    public PreparedStatement prepareInsertQuery(AccountTransactionEntity data) throws SQLException {
        String insertQuery = "INSERT INTO " + tableName + "(account_id, account_broker_id, transaction_date, value) VALUES (?, ?, ?, ?)";
        PreparedStatement preparedStatement = connection.prepareStatement(insertQuery, PreparedStatement.RETURN_GENERATED_KEYS);
        preparedStatement.setInt(1, data.getAccountId());
        preparedStatement.setInt(2, data.getAccountBrokerId());
        preparedStatement.setDate(3, data.getTransactionDate());
        preparedStatement.setDouble(4, data.getValue());
        return preparedStatement;
    }

    @Override
    public PreparedStatement prepareUpdateQuery(AccountTransactionEntity data) throws SQLException {
        String updateQuery = "UPDATE " + tableName + " SET account_id = ?, account_broker_id = ?, transaction_date = ?, value = ? WHERE id = ?";
        PreparedStatement preparedStatement = connection.prepareStatement(updateQuery, PreparedStatement.RETURN_GENERATED_KEYS);
        preparedStatement.setInt(1, data.getAccountId());
        preparedStatement.setInt(2, data.getAccountBrokerId());
        preparedStatement.setDate(3, data.getTransactionDate());
        preparedStatement.setDouble(4, data.getValue());
        preparedStatement.setInt(5, data.getId());
        return preparedStatement;
    }

    @Override
    public List<AccountTransactionEntity> generateEntityData(ResultSet rs) throws SQLException {
        List<AccountTransactionEntity> accountTransactionEntityList = new ArrayList<>();
        while (rs.next()) {
            AccountTransactionEntity accountTransactionEntity = new AccountTransactionEntity();
            accountTransactionEntity.setId(rs.getInt("id"));
            accountTransactionEntity.setAccountId(rs.getInt("account_id"));
            accountTransactionEntity.setAccountBrokerId(rs.getInt("account_broker_id"));
            accountTransactionEntity.setTransactionDate(rs.getDate("transaction_date"));
            accountTransactionEntity.setValue(rs.getDouble("value"));
            accountTransactionEntityList.add(accountTransactionEntity);
        }
        return accountTransactionEntityList;
    }
}
