package com.networth.networthbackend.Repository;

import com.networth.networthbackend.entity.ChartDataEntity;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class ChartDataRepository extends Repository<ChartDataEntity> {

    public ChartDataRepository() {
        tableName = "chart";
    }

    @Override
    public List<ChartDataEntity> generateEntityData(ResultSet rs) throws SQLException {
        return List.of();
    }
}
