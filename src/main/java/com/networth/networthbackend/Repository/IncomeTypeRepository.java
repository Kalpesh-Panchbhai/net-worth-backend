package com.networth.networthbackend.Repository;

import com.networth.networthbackend.entity.IncomeTypeEntity;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class IncomeTypeRepository extends Repository<IncomeTypeEntity> {

    public IncomeTypeRepository() {
        tableName = "income_type";
    }

    @Override
    public PreparedStatement prepareInsertQuery(IncomeTypeEntity data) throws SQLException {
        String insertQuery = "INSERT INTO " + tableName + "(user_id, is_default, name) VALUES (?, ?, ?)";
        PreparedStatement preparedStatement = connection.prepareStatement(insertQuery, PreparedStatement.RETURN_GENERATED_KEYS);
        preparedStatement.setInt(1, data.getUserId());
        preparedStatement.setBoolean(2, data.getIsDefault());
        preparedStatement.setString(3, data.getName());
        return preparedStatement;
    }

    @Override
    public PreparedStatement prepareUpdateQuery(IncomeTypeEntity data) throws SQLException {
        String updateQuery = "UPDATE " + tableName + " SET user_id = ?, is_default = ?, name = ? WHERE id = ?";
        PreparedStatement preparedStatement = connection.prepareStatement(updateQuery, PreparedStatement.RETURN_GENERATED_KEYS);
        preparedStatement.setInt(1, data.getUserId());
        preparedStatement.setBoolean(2, data.getIsDefault());
        preparedStatement.setString(3, data.getName());
        preparedStatement.setInt(4, data.getId());
        return preparedStatement;
    }

    @Override
    public List<IncomeTypeEntity> generateEntityData(ResultSet rs) throws SQLException {
        List<IncomeTypeEntity> incomeTypeEntityList = new ArrayList<>();
        while (rs.next()) {
            IncomeTypeEntity incomeTypeEntity = new IncomeTypeEntity();
            incomeTypeEntity.setId(rs.getInt("id"));
            incomeTypeEntity.setUserId(rs.getInt("user_id"));
            incomeTypeEntity.setIsDefault(rs.getBoolean("is_default"));
            incomeTypeEntity.setName(rs.getString("name"));
            incomeTypeEntityList.add(incomeTypeEntity);
        }
        return incomeTypeEntityList;
    }
}
