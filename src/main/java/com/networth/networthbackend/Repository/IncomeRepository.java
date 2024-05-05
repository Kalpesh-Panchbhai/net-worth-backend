package com.networth.networthbackend.Repository;

import com.networth.networthbackend.entity.IncomeEntity;

public class IncomeRepository extends Repository<IncomeEntity> {

    public IncomeRepository() {
        tableName = "income";
    }
}
