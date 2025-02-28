package com.networth.networthbackend.Repository;

import com.networth.networthbackend.entity.IncomeEntity;
import com.networth.networthbackend.entity.IncomeTagEntity;
import com.networth.networthbackend.entity.IncomeTypeEntity;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        preparedStatement.setString(4, data.getCreditedDate());
        preparedStatement.setString(5, data.getCurrency());
        preparedStatement.setInt(6, data.getIncomeType().getId());
        preparedStatement.setInt(7, data.getIncomeTag().getId());
        return preparedStatement;
    }

    @Override
    public PreparedStatement prepareUpdateQuery(IncomeEntity data) throws SQLException {
        String updateQuery = "UPDATE " + tableName + " SET user_id = ?, amount = ?, tax_paid = ?, credited_date = ?, currency = ?, income_type = ?, income_tag = ? WHERE id = ?";
        PreparedStatement preparedStatement = connection.prepareStatement(updateQuery, PreparedStatement.RETURN_GENERATED_KEYS);
        preparedStatement.setInt(1, data.getUserId());
        preparedStatement.setDouble(2, data.getAmount());
        preparedStatement.setDouble(3, data.getTaxPaid());
        preparedStatement.setString(4, data.getCreditedDate());
        preparedStatement.setString(5, data.getCurrency());
        preparedStatement.setInt(6, data.getIncomeType().getId());
        preparedStatement.setInt(7, data.getIncomeTag().getId());
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
            incomeEntity.setCreditedDate(rs.getString("credited_date"));
            incomeEntity.setCurrency(rs.getString("currency"));
            incomeEntity.setIncomeType(new IncomeTypeEntity(rs.getInt("income_type_id"), rs.getInt("income_type_user_id"), rs.getString("income_type_name"), rs.getBoolean("income_type_is_default")));
            incomeEntity.setIncomeTag(new IncomeTagEntity(rs.getInt("income_tag_id"), rs.getInt("income_tag_user_id"), rs.getString("income_tag_name"), rs.getBoolean("income_tag_is_default")));
            incomeEntityList.add(incomeEntity);
        }
        return incomeEntityList;
    }

    @Override
    public List<IncomeEntity> getBySecondaryField(LinkedHashMap<String, Object> queryMap) {
        try {
            StringBuilder query = new StringBuilder("select i.*, type.id as income_type_id, type.user_id as income_type_user_id, type.name as income_type_name, type.is_default as income_type_is_default, tag.id as income_tag_id, tag.user_id as income_tag_user_id, tag.name as income_tag_name, tag.is_default as income_tag_is_default from income i join income_type type on type.id = i.income_type join income_tag tag on tag.id = i.income_tag where (i.");
            boolean multipleFields = false;
            for (Map.Entry<String, Object> entry : queryMap.entrySet()) {
                if (multipleFields) query.append(" AND ").append(entry.getKey()).append(" = ?");
                else query.append(entry.getKey()).append(" = ?");
                multipleFields = true;
            }
            query.append(")");
            PreparedStatement pstmt = connection.prepareStatement(String.valueOf(query));
            int queryId = 1;
            for (Map.Entry<String, Object> entry : queryMap.entrySet()) {
                pstmt.setObject(queryId++, entry.getValue());
            }
            ResultSet rs = pstmt.executeQuery();
            return generateEntityData(rs);
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
        return null;
    }
}
