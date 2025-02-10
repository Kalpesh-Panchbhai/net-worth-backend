package com.networth.networthbackend.Repository;

import com.networth.networthbackend.entity.IncomeTypeEntity;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class IncomeTypeRepository extends Repository<IncomeTypeEntity> {

    public IncomeTypeRepository() {
        tableName = "income_type";
    }

    @Override
    public List<IncomeTypeEntity> generateEntityData(ResultSet rs) throws SQLException {
        return List.of();
    }
}
