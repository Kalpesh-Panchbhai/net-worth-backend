package com.networth.networthbackend.service;

import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.networth.networthbackend.entity.Income;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class IncomeService extends Service<Income> {

    public IncomeService() {
        tableName = "income";
    }

    @Override
    public List<Income> convertToListOfObject(ScanResult scanResult) {
        List<Income> dataList = new ArrayList<>();
        for (Map<String, AttributeValue> valueMap : scanResult.getItems()) {
            Income income = new Income();
            income.setId(valueMap.get("id").getS());
            income.setAmount(Double.parseDouble(valueMap.get("amount").getN()));
            income.setTaxPaid(Double.parseDouble(valueMap.get("taxPaid").getN()));
            income.setCurrency(valueMap.get("currency").getS());
            income.setIncomeType(valueMap.get("incomeType").getS());
            income.setIncomeTag(valueMap.get("incomeTag").getS());
            income.setCreditedOn(valueMap.get("creditedOn").getS());
            income.setUserId(valueMap.get("userId").getS());
            dataList.add(income);
        }
        return dataList;
    }
}
