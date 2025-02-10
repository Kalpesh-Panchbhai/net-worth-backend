package com.networth.networthbackend.service;

import com.networth.networthbackend.Repository.IncomeTypeRepository;
import com.networth.networthbackend.entity.IncomeTypeEntity;
import com.networth.networthbackend.model.TypeRest;

public class IncomeTypeService extends Service<TypeRest, IncomeTypeEntity> {

    public IncomeTypeService() {
        super(new IncomeTypeRepository());
        secondaryFieldName = "user_id";
    }

    @Override
    public IncomeTypeEntity convertRequestToEntity(TypeRest data) {
        return new IncomeTypeEntity(data.getId(), data.getUserId(), data.getName(), data.getIsDefault());
    }
}
