package com.networth.networthbackend.service;

import com.networth.networthbackend.Repository.IncomeTypeRepository;
import com.networth.networthbackend.entity.IncomeTypeEntity;

import java.util.List;

public class IncomeTypeService extends Service<IncomeTypeEntity> {

    public IncomeTypeService() {
        super(new IncomeTypeRepository());
        secondaryFieldName = List.of("user_id");
    }
}
