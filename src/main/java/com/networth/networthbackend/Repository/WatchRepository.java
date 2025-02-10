package com.networth.networthbackend.Repository;

import com.networth.networthbackend.entity.WatchEntity;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class WatchRepository extends Repository<WatchEntity> {

    public WatchRepository() {
        tableName = "watch";
    }

    @Override
    public PreparedStatement generateInsertQuery(WatchEntity data) throws SQLException {
        return null;
    }

    @Override
    public List<WatchEntity> generateEntityData(ResultSet rs) throws SQLException {
        return List.of();
    }
}
