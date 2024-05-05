package com.networth.networthbackend.Repository;

import com.networth.networthbackend.entity.IncomeTypeEntity;

public class IncomeTypeRepository extends Repository<IncomeTypeEntity> {

    public IncomeTypeRepository() {
        tableName = "income_type";
    }
}
