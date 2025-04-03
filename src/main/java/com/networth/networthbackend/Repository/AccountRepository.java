package com.networth.networthbackend.Repository;

import com.networth.networthbackend.entity.AccountEntity;
import com.networth.networthbackend.entity.AccountValueEntity;

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
        String insertQuery = "INSERT INTO " + tableName + "(name, type, is_active, currency, user_id, current_value, previous_value, invested) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        PreparedStatement preparedStatement = connection.prepareStatement(insertQuery, PreparedStatement.RETURN_GENERATED_KEYS);
        preparedStatement.setString(1, data.getName());
        preparedStatement.setString(2, data.getType());
        preparedStatement.setBoolean(3, data.getActive());
        preparedStatement.setString(4, data.getCurrency());
        preparedStatement.setInt(5, data.getUserId());
        preparedStatement.setDouble(6, 0.0);
        preparedStatement.setDouble(7, 0.0);
        preparedStatement.setDouble(8, 0.0);
        return preparedStatement;
    }

    @Override
    public PreparedStatement prepareUpdateQuery(AccountEntity data) throws SQLException {
        String updateQuery = "UPDATE " + tableName + " SET name = ?, type = ?, is_active = ?, currency = ?, user_id = ?, current_value = ?, previous_value = ?, invested = ? WHERE id = ?";
        PreparedStatement preparedStatement = connection.prepareStatement(updateQuery, PreparedStatement.RETURN_GENERATED_KEYS);
        preparedStatement.setString(1, data.getName());
        preparedStatement.setString(2, data.getType());
        preparedStatement.setBoolean(3, data.getActive());
        preparedStatement.setString(4, data.getCurrency());
        preparedStatement.setInt(5, data.getUserId());
        preparedStatement.setDouble(6, data.getValue().getCurrentValue());
        preparedStatement.setDouble(7, data.getValue().getPreviousValue());
        preparedStatement.setDouble(8, data.getValue().getInvested());
        preparedStatement.setInt(9, data.getId());
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
            accountEntity.setValue(new AccountValueEntity(rs.getDouble("current_value"), rs.getDouble("previous_value"), rs.getDouble("invested")));
            accountEntityList.add(accountEntity);
        }
        return accountEntityList;
    }
}
