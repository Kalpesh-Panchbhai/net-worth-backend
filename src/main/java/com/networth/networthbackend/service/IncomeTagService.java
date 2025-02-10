package com.networth.networthbackend.service;

import com.networth.networthbackend.Repository.IncomeTagRepository;
import com.networth.networthbackend.entity.IncomeTagEntity;

public class IncomeTagService extends Service<IncomeTagEntity> {

    public IncomeTagService() {
        super(new IncomeTagRepository());
        secondaryFieldName = "user_id";
    }
}
