package com.networth.networthbackend.service;

import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networth.networthbackend.entity.Account;
import com.networth.networthbackend.entity.AccountBroker;
import com.networth.networthbackend.entity.AccountTransaction;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class AccountService extends Service<Account> {

    public AccountService() {
        tableName = "account";
    }

    @Override
    public APIGatewayProxyResponseEvent add(APIGatewayProxyRequestEvent apiGatewayRequest, Context context, Class<Account> accountClass) {
        APIGatewayProxyResponseEvent apiGatewayProxyResponseEvent = super.add(apiGatewayRequest, context, accountClass);

        Account account = utility.convertStringToObj(apiGatewayProxyResponseEvent.getBody(), context, Account.class);

        if (!account.getAccountOpenedDate().isBlank()) {
            AccountTransactionService accountTransactionService = new AccountTransactionService();
            APIGatewayProxyRequestEvent apiGatewayProxyRequestEvent = new APIGatewayProxyRequestEvent();
            AccountTransaction accountTransaction = new AccountTransaction(null, account.getId(), account.getAccountOpenedDate(), account.getCurrentBalance(), true, "UPDATE");
            apiGatewayProxyRequestEvent.setBody(accountTransactionService.utility.convertObjToString(accountTransaction, context));
            accountTransactionService.add(apiGatewayProxyRequestEvent, context, AccountTransaction.class);
        }
        return apiGatewayProxyResponseEvent;
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
            account.setAccountOpenedDate("");
            dataList.add(account);
        }
        return dataList;
    }

    @Override
    public APIGatewayProxyResponseEvent delete(APIGatewayProxyRequestEvent apiGatewayRequest, Context context) {
        Map<String, String> pathParams = apiGatewayRequest.getPathParameters();
        String accountId = pathParams.get("id");
        deleteAccount(accountId);
        return super.delete(apiGatewayRequest, context);
    }

    public void deleteAccount(String accountID) {
        AccountBrokerService accountBrokerService = new AccountBrokerService();
        AccountTransactionService accountTransactionService = new AccountTransactionService();
        Account account = dynamoDBMapper.load(Account.class, accountID);
        if (Objects.nonNull(account)) {
            if (account.getAccountType().equals("Broker")) {
                List<AccountBroker> accountBrokerList = accountBrokerService.getAllByUniqueID("accountId", accountID);
                for (AccountBroker accountBroker : accountBrokerList) {
                    List<AccountTransaction> accountTransactionList = accountTransactionService.getAllByUniqueID("accountId", accountBroker.getId());
                    for (AccountTransaction accountTransaction : accountTransactionList) {
                        accountTransactionService.deleteDataByPrimaryField(accountTransaction.getId());
                    }
                    accountBrokerService.deleteDataByPrimaryField(accountBroker.getId());
                }
            } else {
                List<AccountTransaction> accountTransactionList = accountTransactionService.getAllByUniqueID("accountId", accountID);
                for (AccountTransaction accountTransaction : accountTransactionList) {
                    accountTransactionService.deleteDataByPrimaryField(accountTransaction.getId());
                }
            }
        }
    }
}
