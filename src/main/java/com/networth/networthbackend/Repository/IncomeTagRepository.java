package com.networth.networthbackend.Repository;

import com.networth.networthbackend.entity.IncomeTagEntity;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class IncomeTagRepository extends Repository<IncomeTagEntity> {

    public IncomeTagRepository() {
        tableName = "income_tag";
    }

    @Override
    public PreparedStatement generateInsertQuery(IncomeTagEntity data) throws SQLException {
        String insertQuery = "INSERT INTO " + tableName + "(user_id, is_default, name) VALUES (?, ?, ?)";
        PreparedStatement preparedStatement = connection.prepareStatement(insertQuery, PreparedStatement.RETURN_GENERATED_KEYS);
        preparedStatement.setString(1, data.getUserId());
        preparedStatement.setBoolean(2, data.getIsDefault());
        preparedStatement.setString(3, data.getName());
        return preparedStatement;
    }

    @Override
    public List<IncomeTagEntity> generateEntityData(ResultSet rs) throws SQLException {
        List<IncomeTagEntity> incomeTagEntityList = new ArrayList<>();
        while (rs.next()) {
            IncomeTagEntity incomeTagEntity = new IncomeTagEntity();
            incomeTagEntity.setId(rs.getInt("id"));
            incomeTagEntity.setUserId(rs.getString("user_id"));
            incomeTagEntity.setIsDefault(rs.getBoolean("is_default"));
            incomeTagEntity.setName(rs.getString("name"));
            incomeTagEntityList.add(incomeTagEntity);
        }
        return incomeTagEntityList;
    }
}
