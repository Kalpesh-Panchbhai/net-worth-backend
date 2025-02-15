package com.networth.networthbackend.Repository;

import com.networth.networthbackend.entity.ChartEntity;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import static java.sql.Types.INTEGER;

public class ChartRepository extends Repository<ChartEntity> {

    public ChartRepository() {
        tableName = "chart";
    }

    @Override
    public PreparedStatement prepareInsertQuery(ChartEntity data) throws SQLException {
        String insertQuery = "INSERT INTO " + tableName + "(account_id, account_broker_id, date, value) VALUES (?, ?, ?, ?)";
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
        preparedStatement.setString(3, data.getDate());
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
        preparedStatement.setString(3, data.getDate());
        preparedStatement.setDouble(4, data.getValue());
        preparedStatement.setInt(5, data.getId());
        return preparedStatement;
    }

    @Override
    public List<ChartEntity> generateEntityData(ResultSet rs) throws SQLException {
        List<ChartEntity> chartEntityList = new ArrayList<>();
        while (rs.next()) {
            ChartEntity chartEntity = new ChartEntity();
            chartEntity.setValue(rs.getDouble(1));
            chartEntity.setDate(rs.getString(2));
            chartEntityList.add(chartEntity);
        }
        return chartEntityList;
    }

    @Override
    public List<ChartEntity> getBySecondaryField(LinkedHashMap<String, Object> queryMap) throws SQLException {
        try {
            PreparedStatement pstmt = getQuery(queryMap.get("type"), queryMap.get("id"));
            ResultSet rs = pstmt.executeQuery();
            return generateEntityData(rs);
        } catch (Exception e) {
            throw new SQLException(e.getMessage());
        }
    }

    private PreparedStatement getQuery(Object type, Object value) throws SQLException {
        if (type.equals("single_broker")) {
            String query = "select sum(value), date from chart where (account_broker_id in (select id from account_broker where id = ?)) group by date order by date";
            PreparedStatement pstmt = connection.prepareStatement(query);
            pstmt.setObject(1, value);
            return pstmt;
        } else if (type.equals("one_non_broker")) {
            String query = "select sum(value), date from chart where (account_id in (select id from account where id = ?)) group by date order by date";
            PreparedStatement pstmt = connection.prepareStatement(query);
            pstmt.setObject(1, value);
            return pstmt;
        } else if (type.equals("one_broker")) {
            String query = "select sum(value), date from chart where (account_broker_id in (select id from account_broker where account_id in (select id from account where id = ?))) group by date order by date";
            PreparedStatement pstmt = connection.prepareStatement(query);
            pstmt.setObject(1, value);
            return pstmt;
        } else if (type.equals("non_broker_type")) {
            String query = "select sum(value), date from chart where (account_id in (select id from account where type = ?)) group by date order by date";
            PreparedStatement pstmt = connection.prepareStatement(query);
            pstmt.setObject(1, value);
            return pstmt;
        } else if (type.equals("broker_type")) {
            String query = "select sum(value), date from chart where (account_broker_id in (select id from account_broker where account_id in (select id from account where type = 'Broker'))) group by date order by date";
            return connection.prepareStatement(query);
        } else if (type.equals("all_watch")) {
            String query = "select sum(value), date from chart group by date order by date";
            return connection.prepareStatement(query);
        } else if (type.equals("non_all_watch")) {
            String query = "select sum(value), date from chart where account_id in (select id from account where id in (select account_id from watch_account where watch_id = ?) and type != 'Broker') OR account_broker_id in (select id from account_broker where account_id in (select id from account where id in (select account_id from watch_account where watch_id = ?) and type = 'Broker')) group by date order by date";
            PreparedStatement pstmt = connection.prepareStatement(query);
            pstmt.setObject(1, value);
            pstmt.setObject(2, value);
            return pstmt;
        }
        return null;
    }
}
