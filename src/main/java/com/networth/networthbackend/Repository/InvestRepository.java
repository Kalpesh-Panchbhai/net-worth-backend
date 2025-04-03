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
        String insertQuery = "INSERT INTO " + tableName + "(account_broker_id, date, value) VALUES (?, ?, ?)";
        PreparedStatement preparedStatement = connection.prepareStatement(insertQuery, PreparedStatement.RETURN_GENERATED_KEYS);
        preparedStatement.setInt(1, data.getAccountBrokerId());
        preparedStatement.setString(2, data.getDate());
        preparedStatement.setDouble(3, data.getValue());
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
            investEntity.setAccountBrokerId(rs.getInt("account_broker_id"));
            investEntity.setDate(rs.getString("date"));
            investEntity.setValue(rs.getDouble("value"));
            investEntityList.add(investEntity);
        }
        return investEntityList;
    }
}
