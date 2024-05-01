package com.networth.networthbackend.service;

import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.networth.networthbackend.entity.IncomeTag;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class IncomeTagService extends Service<IncomeTag> {

    public IncomeTagService() {
        tableName = "income_tag";
    }

    @Override
    public List<IncomeTag> convertToListOfObject(ScanResult scanResult) {
        List<IncomeTag> dataList = new ArrayList<>();
        for (Map<String, AttributeValue> valueMap : scanResult.getItems()) {
            IncomeTag incomeTag = new IncomeTag();
            incomeTag.setId(valueMap.get("id").getS());
            incomeTag.setUserId(valueMap.get("userId").getS());
            incomeTag.setName(valueMap.get("name").getS());
            incomeTag.setIsDefault(valueMap.get("isDefault").getBOOL());
            dataList.add(incomeTag);
        }
        return dataList;
    }
}
