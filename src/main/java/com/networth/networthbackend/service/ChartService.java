package com.networth.networthbackend.service;

import com.networth.networthbackend.Repository.ChartRepository;
import com.networth.networthbackend.entity.ChartEntity;

import java.util.List;

public class ChartService extends Service<ChartEntity> {

    public ChartService() {
        super(new ChartRepository());
        secondaryFieldNameList = List.of("account_id", "account_broker_id");
    }
}
