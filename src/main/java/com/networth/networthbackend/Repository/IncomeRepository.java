package com.networth.networthbackend.Repository;

import com.networth.networthbackend.entity.IncomeEntity;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class IncomeRepository extends Repository<IncomeEntity> {

    public IncomeRepository() {
        tableName = "income";
    }

    @Override
    public PreparedStatement prepareInsertQuery(IncomeEntity data) throws SQLException {
        String insertQuery = "INSERT INTO " + tableName + "(user_id, amount, tax_paid, credited_date, currency, income_type, income_tag) VALUES (?, ?, ?, ?, ?, ?, ?)";
        PreparedStatement preparedStatement = connection.prepareStatement(insertQuery, PreparedStatement.RETURN_GENERATED_KEYS);
        preparedStatement.setInt(1, data.getUserId());
        preparedStatement.setDouble(2, data.getAmount());
        preparedStatement.setDouble(3, data.getTaxPaid());
        preparedStatement.setDate(4, data.getCreditedDate());
        preparedStatement.setString(5, data.getCurrency());
        preparedStatement.setInt(6, data.getIncomeType());
        preparedStatement.setInt(7, data.getIncomeTag());
        return preparedStatement;
    }

    @Override
    public PreparedStatement prepareUpdateQuery(IncomeEntity data) throws SQLException {
        String updateQuery = "UPDATE " + tableName + " SET user_id = ?, amount = ?, tax_paid = ?, credited_on = ?, currency = ?, income_type = ?, income_tag = ? WHERE id = ?";
        PreparedStatement preparedStatement = connection.prepareStatement(updateQuery, PreparedStatement.RETURN_GENERATED_KEYS);
        preparedStatement.setInt(1, data.getUserId());
        preparedStatement.setDouble(2, data.getAmount());
        preparedStatement.setDouble(3, data.getTaxPaid());
        preparedStatement.setDate(4, data.getCreditedDate());
        preparedStatement.setString(5, data.getCurrency());
        preparedStatement.setInt(6, data.getIncomeType());
        preparedStatement.setInt(7, data.getIncomeTag());
        preparedStatement.setInt(8, data.getId());
        return preparedStatement;
    }

    @Override
    public List<IncomeEntity> generateEntityData(ResultSet rs) throws SQLException {
        List<IncomeEntity> incomeEntityList = new ArrayList<>();
        while (rs.next()) {
            IncomeEntity incomeEntity = new IncomeEntity();
            incomeEntity.setId(rs.getInt("id"));
            incomeEntity.setUserId(rs.getInt("user_id"));
            incomeEntity.setAmount(rs.getDouble("amount"));
            incomeEntity.setTaxPaid(rs.getDouble("tax_paid"));
            incomeEntity.setCreditedDate(rs.getDate("credited_on"));
            incomeEntity.setCurrency(rs.getString("currency"));
            incomeEntity.setIncomeType(rs.getInt("income_type"));
            incomeEntity.setIncomeTag(rs.getInt("income_tag"));
            incomeEntityList.add(incomeEntity);
        }
        return incomeEntityList;
    }
}
