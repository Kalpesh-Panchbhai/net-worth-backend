package com.networth.networthbackend.service;

import com.networth.networthbackend.Repository.WatchRepository;
import com.networth.networthbackend.entity.WatchEntity;

import java.util.List;

public class WatchService extends Service<WatchEntity> {

    public WatchService() {
        super(new WatchRepository());
        secondaryFieldName = List.of("user_id");
    }
}
