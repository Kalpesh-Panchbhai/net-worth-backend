package com.networth.networthbackend.Repository;

import com.networth.networthbackend.entity.IncomeTagEntity;

public class IncomeTagRepository extends Repository<IncomeTagEntity> {

    public IncomeTagRepository() {
        tableName = "income_tag";
    }
}
