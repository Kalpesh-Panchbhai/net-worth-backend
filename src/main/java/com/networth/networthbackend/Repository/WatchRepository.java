package com.networth.networthbackend.Repository;

import com.networth.networthbackend.entity.WatchEntity;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class WatchRepository extends Repository<WatchEntity> {

    public WatchRepository() {
        tableName = "watch";
    }

    @Override
    public PreparedStatement prepareInsertQuery(WatchEntity data) throws SQLException {
        String insertQuery = "INSERT INTO " + tableName + "(user_id, name) VALUES (?, ?, ?)";
        PreparedStatement preparedStatement = connection.prepareStatement(insertQuery, PreparedStatement.RETURN_GENERATED_KEYS);
        preparedStatement.setInt(1, data.getUserId());
        preparedStatement.setString(2, data.getName());
        return preparedStatement;
    }

    @Override
    public PreparedStatement prepareUpdateQuery(WatchEntity data) throws SQLException {
        String updateQuery = "UPDATE " + tableName + " SET user_id = ?, name = ? WHERE id = ?";
        PreparedStatement preparedStatement = connection.prepareStatement(updateQuery, PreparedStatement.RETURN_GENERATED_KEYS);
        preparedStatement.setInt(1, data.getUserId());
        preparedStatement.setString(2, data.getName());
        preparedStatement.setInt(3, data.getId());
        return preparedStatement;
    }

    @Override
    public List<WatchEntity> generateEntityData(ResultSet rs) throws SQLException {
        List<WatchEntity> watchEntityList = new ArrayList<>();
        while (rs.next()) {
            WatchEntity watchEntity = new WatchEntity();
            watchEntity.setId(rs.getInt("id"));
            watchEntity.setUserId(rs.getInt("user_id"));
            watchEntity.setName(rs.getString("name"));
            watchEntityList.add(watchEntity);
        }
        return watchEntityList;
    }
}
