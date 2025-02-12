package com.networth.networthbackend.Repository;

import com.networth.networthbackend.entity.ChartEntity;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static java.sql.Types.INTEGER;

public class ChartRepository extends Repository<ChartEntity> {

    public ChartRepository() {
        tableName = "account_broker";
    }

    @Override
    public PreparedStatement prepareInsertQuery(ChartEntity data) throws SQLException {
        String insertQuery = "INSERT INTO " + tableName + "(account_id, account_broker_id, date, value) VALUES (?, ?. ?, ?)";
        PreparedStatement preparedStatement = connection.prepareStatement(insertQuery, PreparedStatement.RETURN_GENERATED_KEYS);
        if (data.getAccountId() != null) {
            preparedStatement.setInt(1, data.getAccountId());
        } else {
            preparedStatement.setNull(1, INTEGER);
        }
        if (data.getAccountBrokerId() != null) {
            preparedStatement.setInt(2, data.getAccountBrokerId());
        } else {
            preparedStatement.setNull(2, INTEGER);
        }
        preparedStatement.setDate(3, data.getDate());
        preparedStatement.setDouble(4, data.getValue());
        return preparedStatement;
    }

    @Override
    public PreparedStatement prepareUpdateQuery(ChartEntity data) throws SQLException {
        String updateQuery = "UPDATE " + tableName + " SET account_id = ?, account_broker_id = ?, date = ?, value = ? WHERE id = ?";
        PreparedStatement preparedStatement = connection.prepareStatement(updateQuery, PreparedStatement.RETURN_GENERATED_KEYS);
        if (data.getAccountId() != null) {
            preparedStatement.setInt(1, data.getAccountId());
        } else {
            preparedStatement.setNull(1, INTEGER);
        }
        if (data.getAccountBrokerId() != null) {
            preparedStatement.setInt(2, data.getAccountBrokerId());
        } else {
            preparedStatement.setNull(2, INTEGER);
        }
        preparedStatement.setDate(3, data.getDate());
        preparedStatement.setDouble(4, data.getValue());
        preparedStatement.setInt(5, data.getId());
        return preparedStatement;
    }

    @Override
    public List<ChartEntity> generateEntityData(ResultSet rs) throws SQLException {
        List<ChartEntity> chartEntityList = new ArrayList<>();
        while (rs.next()) {
            ChartEntity chartEntity = new ChartEntity();
            chartEntity.setId(rs.getInt("id"));
            chartEntity.setAccountId(rs.getInt("account_id"));
            chartEntity.setAccountBrokerId(rs.getInt("account_broker_id"));
            chartEntity.setDate(rs.getDate("date"));
            chartEntity.setValue(rs.getDouble("value"));
            chartEntityList.add(chartEntity);
        }
        return chartEntityList;
    }
}
