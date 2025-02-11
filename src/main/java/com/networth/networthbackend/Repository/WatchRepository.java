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
    public PreparedStatement generateInsertQuery(WatchEntity data) throws SQLException {
        String insertQuery = "INSERT INTO " + tableName + "(user_id, name) VALUES (?, ?, ?)";
        PreparedStatement preparedStatement = connection.prepareStatement(insertQuery, PreparedStatement.RETURN_GENERATED_KEYS);
        preparedStatement.setString(1, data.getUserId());
        preparedStatement.setString(2, data.getName());
        return preparedStatement;
    }

    @Override
    public List<WatchEntity> generateEntityData(ResultSet rs) throws SQLException {
        List<WatchEntity> watchEntityList = new ArrayList<>();
        while (rs.next()) {
            WatchEntity watchEntity = new WatchEntity();
            watchEntity.setId(rs.getInt("id"));
            watchEntity.setUserId(rs.getString("user_id"));
            watchEntity.setName(rs.getString("name"));
            watchEntityList.add(watchEntity);
        }
        return watchEntityList;
    }
}
