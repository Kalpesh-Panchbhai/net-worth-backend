package com.networth.networthbackend.Repository;

import com.networth.networthbackend.entity.InvestEntity;
import com.networth.networthbackend.entity.WatchAccountEntity;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static java.sql.Types.INTEGER;

public class InvestRepository extends Repository<InvestEntity> {

    public InvestRepository() {
        tableName = "invest";
    }

    @Override
    public PreparedStatement prepareInsertQuery(InvestEntity data) throws SQLException {
        String insertQuery = "INSERT INTO " + tableName + "(account_id, account_broker_id, date, value) VALUES (?, ?, ?, ?)";
        PreparedStatement preparedStatement = connection.prepareStatement(insertQuery, PreparedStatement.RETURN_GENERATED_KEYS);
        if (data.getAccountId() != null && data.getAccountId() != 0) {
            preparedStatement.setInt(1, data.getAccountId());
        } else {
            preparedStatement.setNull(1, INTEGER);
        }
        if (data.getAccountBrokerId() != null && data.getAccountBrokerId() != 0) {
            preparedStatement.setInt(2, data.getAccountBrokerId());
        } else {
            preparedStatement.setNull(2, INTEGER);
        }
        preparedStatement.setString(3, data.getDate());
        preparedStatement.setDouble(4, data.getValue());
        return preparedStatement;
    }

    @Override
    public PreparedStatement prepareUpdateQuery(InvestEntity data) throws SQLException {
//        METHOD NOT NEEDED
        return null;
    }

    @Override
    public List<InvestEntity> generateEntityData(ResultSet rs) throws SQLException {
        List<InvestEntity> investEntityList = new ArrayList<>();
        while (rs.next()) {
            InvestEntity investEntity = new InvestEntity();
            investEntity.setId(rs.getInt("id"));
            investEntity.setAccountId(rs.getInt("account_id"));
            investEntity.setAccountBrokerId(rs.getInt("account_broker_id"));
            investEntity.setDate(rs.getString("date"));
            investEntity.setValue(rs.getDouble("value"));
            investEntityList.add(investEntity);
        }
        return investEntityList;
    }
}
