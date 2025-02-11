package com.networth.networthbackend.Repository;

import com.networth.networthbackend.entity.AccountEntity;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
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
        preparedStatement.setString(5, data.getUserId());
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
        preparedStatement.setString(5, data.getUserId());
        preparedStatement.setInt(6, data.getId());
        return preparedStatement;
    }

    @Override
    public List<AccountEntity> generateEntityData(ResultSet rs) throws SQLException {
        List<AccountEntity> accountEntityList = new ArrayList<>();
        while (rs.next()) {
            AccountEntity accountEntity = new AccountEntity();
            accountEntity.setId(rs.getInt("id"));
            accountEntity.setUserId(rs.getString("user_id"));
            accountEntity.setName(rs.getString("name"));
            accountEntity.setType(rs.getString("type"));
            accountEntity.setActive(rs.getBoolean("is_active"));
            accountEntity.setCurrency(rs.getString("currency"));
            accountEntityList.add(accountEntity);
        }
        return accountEntityList;
    }
}
