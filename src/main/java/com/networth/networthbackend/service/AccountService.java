package com.networth.networthbackend.service;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networth.networthbackend.Repository.AccountBalanceRepository;
import com.networth.networthbackend.Repository.AccountRepository;
import com.networth.networthbackend.entity.AccountEntity;
import com.networth.networthbackend.entity.WatchEntity;
import com.networth.networthbackend.model.AccountRest;
import com.networth.networthbackend.model.RestResponse;
import com.networth.networthbackend.utility.Utility;

import java.util.List;
import java.util.stream.Collectors;

public class AccountService extends Service<AccountRest, AccountEntity> {

    private final AccountBrokerService accountBrokerService = new AccountBrokerService();
    private final AccountBalanceRepository accountBalanceRepository = new AccountBalanceRepository();
    private final WatchService watchService = new WatchService();

    public AccountService() {
        super(new AccountRepository());
        secondaryFieldName = "user_id";
    }

    @Override
    public APIGatewayProxyResponseEvent post(APIGatewayProxyRequestEvent apiGatewayRequest, Context context, Class<AccountRest> type) {
        AccountRest requestData = utilityRest.convertStringToObj(apiGatewayRequest.getBody(), context, type);
        AccountEntity savedData = convertRequestToEntity(requestData);
        repository.save(savedData);
        String accountId = savedData.getId();
        String userId = savedData.getUserId();
        addAccountInDefaultWatch(userId, accountId);
        RestResponse restResponse = Utility.generateRestResponse("success");
        String jsonBody = new Utility<RestResponse>().convertObjToString(restResponse, context);
        context.getLogger().log("data saved successfully to dynamodb:::" + jsonBody);
        return createAPIResponse(jsonBody, 201, Utility.createHeaders());
    }

    private void addAccountInDefaultWatch(String userId, String accountId) {
        List<WatchEntity> watchEntityList = watchService.repository.getBySecondaryField("user_id", userId);
        String watchAllId = watchEntityList.stream().filter((data) -> data.getName().equals("All")).collect(Collectors.toList()).get(0).getId();

        WatchEntity watchEntity = watchService.repository.getByPrimaryField(watchAllId, WatchEntity.class);
        watchEntity.getAccountList().add(accountId);
        watchService.repository.update(watchEntity);
    }

    @Override
    public AccountEntity convertRequestToEntity(AccountRest accountRestRequest) {
        AccountEntity accountEntity = new AccountEntity();
        accountEntity.setId(accountRestRequest.getId());
        accountEntity.setUserId(accountRestRequest.getUserId());
        accountEntity.setCurrency(accountRestRequest.getCurrency());
        accountEntity.setActive(accountRestRequest.getActive());
        return accountEntity;
    }
}
