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
        String insertQuery = "INSERT INTO " + tableName + "(account_id, account_broker_id, date, value, invested) VALUES (?, ?, ?, ?, ?)";
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
        if (data.getInvested() != null) {
            preparedStatement.setDouble(5, data.getInvested());
        } else {
            preparedStatement.setDouble(5, 0.0);
        }
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
            chartEntity.setInvested(rs.getDouble(2));
            chartEntity.setDate(rs.getString(3));
            chartEntityList.add(chartEntity);
        }
        return chartEntityList;
    }

    public List<ChartEntity> generateEntityDataDhan(ResultSet rs) throws SQLException {
        List<ChartEntity> chartEntityList = new ArrayList<>();
        while (rs.next()) {
            ChartEntity chartEntity = new ChartEntity();
            chartEntity.setId(rs.getInt(1));
            chartEntity.setAccountId(rs.getInt(2));
            chartEntity.setAccountBrokerId(rs.getInt(3));
            chartEntity.setDate(rs.getString(4));
            chartEntity.setValue(rs.getDouble(5));
            chartEntity.setInvested(rs.getDouble(6));
            chartEntityList.add(chartEntity);
        }
        return chartEntityList;
    }

    @Override
    public void delete(Object fieldName, Object fieldValue) throws SQLException {
        try {
            String query;
            if (fieldValue.toString().equals("17")) {
                query = "delete from " + tableName + " where " + fieldName + " = ? AND date BETWEEN DATE_SUB(CURDATE(), INTERVAL 0 DAY) AND CURDATE()";
            } else {
                query = "delete from " + tableName + " where " + fieldName + " = ? AND date BETWEEN DATE_SUB(CURDATE(), INTERVAL 1 WEEK) AND CURDATE()";
            }
            PreparedStatement pstmt = connection.prepareStatement(query);
            pstmt.setObject(1, fieldValue);
            pstmt.execute();
        } catch (Exception e) {
            throw new SQLException(e.getMessage());
        }
    }

    @Override
    public List<ChartEntity> getBySecondaryField(LinkedHashMap<String, Object> queryMap) throws SQLException {
        try {
            PreparedStatement pstmt = getQuery(queryMap.get("type"), queryMap.get("id"), queryMap.get("range"));
            ResultSet rs = pstmt.executeQuery();
            return generateEntityData(rs);
        } catch (Exception e) {
            throw new SQLException(e.getMessage());
        }
    }

    public List<ChartEntity> getBySecondaryField(String id) throws SQLException {
        try {
            PreparedStatement pstmt = connection.prepareStatement("select * from " + tableName + " where account_id = ?");
            pstmt.setInt(1, Integer.parseInt(id));
            ResultSet rs = pstmt.executeQuery();
            return generateEntityDataDhan(rs);
        } catch (Exception e) {
            throw new SQLException(e.getMessage());
        }
    }

    private PreparedStatement getQuery(Object type, Object value, Object range) throws SQLException {
        String rangeQuery = getRangeQuery(range);
        if (type.equals("single_broker")) {
            String query = "select sum(value), sum(invested), date from chart where (account_broker_id in (select id from account_broker where id = ?))" + rangeQuery + " group by date order by date";
            PreparedStatement pstmt = connection.prepareStatement(query);
            pstmt.setObject(1, value);
            return pstmt;
        } else if (type.equals("one_non_broker")) {
            String query = "select sum(value), sum(invested), date from chart where (account_id in (select id from account where id = ?))" + rangeQuery + " group by date order by date";
            PreparedStatement pstmt = connection.prepareStatement(query);
            pstmt.setObject(1, value);
            return pstmt;
        } else if (type.equals("one_broker")) {
            String query = "select sum(value), sum(invested), date from chart where (account_broker_id in (select id from account_broker where account_id in (select id from account where id = ?)))" + rangeQuery + " group by date order by date";
            PreparedStatement pstmt = connection.prepareStatement(query);
            pstmt.setObject(1, value);
            return pstmt;
        } else if (type.equals("non_broker_type")) {
            String query = "select sum(value), sum(invested), date from chart where (account_id in (select id from account where type = ?))" + rangeQuery + " group by date order by date";
            PreparedStatement pstmt = connection.prepareStatement(query);
            pstmt.setObject(1, value);
            return pstmt;
        } else if (type.equals("broker_type")) {
            String query = "select sum(value), sum(invested), date from chart where (account_broker_id in (select id from account_broker where account_id in (select id from account where type = 'Broker')))" + rangeQuery + " group by date order by date";
            return connection.prepareStatement(query);
        } else if (type.equals("all_watch")) {
            String query = "select sum(value), sum(invested), date from chart where true" + rangeQuery + " group by date order by date";
            return connection.prepareStatement(query);
        } else if (type.equals("non_all_watch")) {
            String query = "select sum(value), sum(invested), date from chart where (account_id in (select id from account where id in (select account_id from watch_account where watch_id = ?) and type != 'Broker') OR account_broker_id in (select id from account_broker where account_id in (select id from account where id in (select account_id from watch_account where watch_id = ?) and type = 'Broker')))" + rangeQuery + " group by date order by date";
            PreparedStatement pstmt = connection.prepareStatement(query);
            pstmt.setObject(1, value);
            pstmt.setObject(2, value);
            return pstmt;
        }
        return null;
    }

    private String getRangeQuery(Object range) {
        if (range.equals("1M")) {
            return " AND date BETWEEN DATE_SUB(CURDATE(), INTERVAL 1 MONTH) AND CURDATE()";
        } else if (range.equals("3M")) {
            return " AND date BETWEEN DATE_SUB(CURDATE(), INTERVAL 3 MONTH) AND CURDATE()";
        } else if (range.equals("6M")) {
            return " AND date BETWEEN DATE_SUB(CURDATE(), INTERVAL 6 MONTH) AND CURDATE()";
        } else if (range.equals("1Y")) {
            return " AND date BETWEEN DATE_SUB(CURDATE(), INTERVAL 1 YEAR) AND CURDATE()";
        } else if (range.equals("2Y")) {
            return " AND date BETWEEN DATE_SUB(CURDATE(), INTERVAL 2 YEAR) AND CURDATE()";
        } else if (range.equals("5Y")) {
            return " AND date BETWEEN DATE_SUB(CURDATE(), INTERVAL 5 YEAR) AND CURDATE()";
        } else {
            return "";
        }
    }
}
