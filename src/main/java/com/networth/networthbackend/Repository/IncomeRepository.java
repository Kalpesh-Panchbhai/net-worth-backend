package com.networth.networthbackend.Repository;

import com.networth.networthbackend.entity.IncomeEntity;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class IncomeRepository extends Repository<IncomeEntity> {

    public IncomeRepository() {
        tableName = "income";
    }

    @Override
    public PreparedStatement generateInsertQuery(IncomeEntity data) throws SQLException {
        String insertQuery = "INSERT INTO " + tableName + "(name, type, is_active, currency, user_id) VALUES (?, ?. ?, ?, ?)";
        PreparedStatement preparedStatement = connection.prepareStatement(insertQuery, PreparedStatement.RETURN_GENERATED_KEYS);
//        preparedStatement.setString(1, data.getName());
//        preparedStatement.setString(2, data.getType());
//        preparedStatement.setBoolean(3, data.getActive());
//        preparedStatement.setString(4, data.getCurrency());
//        preparedStatement.setString(5, data.getUserId());
        return preparedStatement;
    }

    @Override
    public List<IncomeEntity> generateEntityData(ResultSet rs) throws SQLException {
        return List.of();
    }
}
