package com.networth.networthbackend.service;

import com.networth.networthbackend.Repository.IncomeRepository;
import com.networth.networthbackend.entity.IncomeEntity;
import com.networth.networthbackend.model.IncomeRest;

public class IncomeService extends Service<IncomeRest, IncomeEntity> {

    public IncomeService() {
        super(new IncomeRepository());
        secondaryFieldName = "user_id";
    }
}
