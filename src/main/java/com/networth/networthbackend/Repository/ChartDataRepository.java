package com.networth.networthbackend.Repository;

import com.networth.networthbackend.entity.ChartDataEntity;

public class ChartDataRepository extends Repository<ChartDataEntity> {

    public ChartDataRepository() {
        tableName = "chart";
    }
}
