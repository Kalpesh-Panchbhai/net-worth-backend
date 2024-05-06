package com.networth.networthbackend.Repository;

import com.networth.networthbackend.entity.WatchEntity;

public class WatchRepository extends Repository<WatchEntity> {

    public WatchRepository() {
        tableName = "watch";
    }
}
