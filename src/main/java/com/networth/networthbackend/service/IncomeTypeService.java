package com.networth.networthbackend.service;

import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.networth.networthbackend.entity.IncomeType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class IncomeTypeService extends Service<IncomeType> {

    public IncomeTypeService() {
        tableName = "income_type";
    }

    @Override
    public List<IncomeType> convertToListOfObject(ScanResult scanResult) {
        List<IncomeType> dataList = new ArrayList<>();
        for (Map<String, AttributeValue> valueMap : scanResult.getItems()) {
            IncomeType incomeType = new IncomeType();
            incomeType.setId(valueMap.get("id").getS());
            incomeType.setUserId(valueMap.get("userId").getS());
            incomeType.setName(valueMap.get("name").getS());
            incomeType.setIsDefault(valueMap.get("isDefault").getBOOL());
            dataList.add(incomeType);
        }
        return dataList;
    }
}
