package com.networth.networthbackend.service;

import com.networth.networthbackend.Repository.IncomeRepository;
import com.networth.networthbackend.entity.IncomeEntity;

public class IncomeService extends Service<IncomeEntity> {

    public IncomeService() {
        super(new IncomeRepository());
        secondaryFieldName = "user_id";
    }
}
