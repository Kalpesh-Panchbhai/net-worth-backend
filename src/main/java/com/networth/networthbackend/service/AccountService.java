package com.networth.networthbackend.service;

import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.networth.networthbackend.entity.Account;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AccountService extends Service<Account> {

    public AccountService() {
        tableName = "account";
    }

    @Override
    public List<Account> convertToListOfObject(ScanResult scanResult) {
        List<Account> dataList = new ArrayList<>();
        for (Map<String, AttributeValue> valueMap : scanResult.getItems()) {
            Account account = new Account();
            account.setId(valueMap.get("id").getS());
            account.setAccountType(valueMap.get("accountType").getS());
            if (valueMap.containsKey("loanType")) {
                account.setLoanType(valueMap.get("loanType").getS());
            } else {
                account.setLoanType("");
            }
            account.setAccountName(valueMap.get("accountName").getS());
            account.setCurrentBalance(Double.parseDouble(valueMap.get("currentBalance").getN()));
            account.setPaymentReminder(valueMap.get("paymentReminder").getBOOL());
            account.setPaymentDate(Integer.parseInt(valueMap.get("paymentDate").getN()));
            account.setCurrency(valueMap.get("currency").getS());
            account.setActive(valueMap.get("active").getBOOL());
            account.setUserId(valueMap.get("userId").getS());
            dataList.add(account);
        }
        return dataList;
    }
}
