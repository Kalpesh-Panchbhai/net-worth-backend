package com.networth.networthbackend.service;

import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networth.networthbackend.entity.User;
import com.networth.networthbackend.utility.Utility;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UserService extends Service<User> {
    private final Utility<User> utility = new Utility<>();

    public UserService() {
        tableName = "user";
    }

    @Override
    public APIGatewayProxyResponseEvent save(APIGatewayProxyRequestEvent apiGatewayRequest, Context context, Class<User> aClass) {
        initDynamoDB();
        User user = utility.convertStringToObj(apiGatewayRequest.getBody(), context, User.class);
        if (checkIfUserAlreadyExist(user, context)) {
            context.getLogger().log("User already exist in dynamodb:::" + jsonBody);
            return createAPIResponse(jsonBody, 200, Utility.createHeaders());
        } else {
            dynamoDBMapper.save(user);
            jsonBody = utility.convertObjToString(user, context);
            context.getLogger().log("data saved successfully to dynamodb:::" + jsonBody);
            return createAPIResponse(jsonBody, 201, Utility.createHeaders());
        }
    }

    @Override
    public List<User> convertToListOfObject(ScanResult scanResult) {
        return null;
    }

    public Boolean checkIfUserAlreadyExist(User user, Context context) {
        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":val", new AttributeValue().withS(user.getUserId()));

        ScanRequest scanRequest = new ScanRequest().withTableName(tableName).withFilterExpression("userId = :val").withExpressionAttributeValues(expressionAttributeValues);
        ScanResult scanResult = client.scan(scanRequest);
        if (scanResult.getCount() > 0) {
            user.setId(scanResult.getItems().get(0).get("id").getS());
            jsonBody = utility.convertObjToString(user, context);
            return true;
        } else {
            return false;
        }
    }

}
