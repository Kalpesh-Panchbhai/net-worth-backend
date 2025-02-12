package com.networth.networthbackend.Repository;

import com.networth.networthbackend.entity.WatchAccountEntity;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class WatchAccountRepository extends Repository<WatchAccountEntity> {

    public WatchAccountRepository() {
        tableName = "watch_account";
    }

    @Override
    public PreparedStatement prepareInsertQuery(WatchAccountEntity data) throws SQLException {
        String insertQuery = "INSERT INTO " + tableName + "(account_id, watch_id) VALUES (?, ?)";
        PreparedStatement preparedStatement = connection.prepareStatement(insertQuery, PreparedStatement.RETURN_GENERATED_KEYS);
        preparedStatement.setInt(1, data.getAccountId());
        preparedStatement.setInt(2, data.getWatchId());
        return preparedStatement;
    }

    @Override
    public PreparedStatement prepareUpdateQuery(WatchAccountEntity data) throws SQLException {
        String updateQuery = "UPDATE " + tableName + " SET account_id = ?, watch_id = ? WHERE id = ?";
        PreparedStatement preparedStatement = connection.prepareStatement(updateQuery, PreparedStatement.RETURN_GENERATED_KEYS);
        preparedStatement.setInt(1, data.getAccountId());
        preparedStatement.setInt(2, data.getWatchId());
        preparedStatement.setInt(3, data.getId());
        return preparedStatement;
    }

    @Override
    public List<WatchAccountEntity> generateEntityData(ResultSet rs) throws SQLException {
        List<WatchAccountEntity> watchEntityList = new ArrayList<>();
        while (rs.next()) {
            WatchAccountEntity watchEntity = new WatchAccountEntity();
            watchEntity.setId(rs.getInt("id"));
            watchEntity.setAccountId(rs.getInt("account_id"));
            watchEntity.setWatchId(rs.getInt("watch_id"));
            watchEntityList.add(watchEntity);
        }
        return watchEntityList;
    }
}
