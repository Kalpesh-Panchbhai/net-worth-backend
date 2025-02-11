package com.networth.networthbackend.service;

import com.networth.networthbackend.Repository.IncomeRepository;
import com.networth.networthbackend.entity.IncomeEntity;

import java.util.List;

public class IncomeService extends Service<IncomeEntity> {

    public IncomeService() {
        super(new IncomeRepository());
        secondaryFieldName = List.of("user_id");
    }
}
