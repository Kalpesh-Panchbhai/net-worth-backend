package com.networth.networthbackend.service;

import com.networth.networthbackend.Repository.IncomeTagRepository;
import com.networth.networthbackend.entity.IncomeTagEntity;
import com.networth.networthbackend.model.TypeRest;

public class IncomeTagService extends Service<TypeRest, IncomeTagEntity> {

    public IncomeTagService() {
        super(new IncomeTagRepository());
        secondaryFieldName = "user_id";
    }
}
