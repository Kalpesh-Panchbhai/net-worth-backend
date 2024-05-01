package com.networth.networthbackend;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networth.networthbackend.entity.Employee;
import com.networth.networthbackend.utility.Utility;

import java.util.List;
import java.util.Map;

public class EmployeeService {
    private static String jsonBody = null;
    private DynamoDBMapper dynamoDBMapper;

    public APIGatewayProxyResponseEvent saveEmployee(APIGatewayProxyRequestEvent apiGatewayRequest, Context context) {
        initDynamoDB();
        Employee employee = Utility.convertStringToObj(apiGatewayRequest.getBody(), context);
        dynamoDBMapper.save(employee);
        jsonBody = Utility.convertObjToString(employee, context);
        context.getLogger().log("data saved successfully to dynamodb:::" + jsonBody);
        return createAPIResponse(jsonBody, 201, Utility.createHeaders());
    }

    public void saveEmployee() {
        initDynamoDB();
        Employee employee = new Employee(null, "Kalpesh", "kalpesh@gmai.com");
        dynamoDBMapper.save(employee);
    }

    public APIGatewayProxyResponseEvent getEmployeeById(APIGatewayProxyRequestEvent apiGatewayRequest, Context context) {
        initDynamoDB();
        String empId = apiGatewayRequest.getPathParameters().get("empId");
        Employee employee = dynamoDBMapper.load(Employee.class, empId);
        if (employee != null) {
            jsonBody = Utility.convertObjToString(employee, context);
            context.getLogger().log("fetch employee By ID:::" + jsonBody);
            return createAPIResponse(jsonBody, 200, Utility.createHeaders());
        } else {
            jsonBody = "Employee Not Found Exception :" + empId;
            return createAPIResponse(jsonBody, 400, Utility.createHeaders());
        }

    }

    public APIGatewayProxyResponseEvent getEmployees(APIGatewayProxyRequestEvent apiGatewayRequest, Context context) {
        initDynamoDB();
        List<Employee> employees = dynamoDBMapper.scan(Employee.class, new DynamoDBScanExpression());
        jsonBody = Utility.convertListOfObjToString(employees, context);
        context.getLogger().log("fetch employee List:::" + jsonBody);
        return createAPIResponse(jsonBody, 200, Utility.createHeaders());
    }

    public APIGatewayProxyResponseEvent deleteEmployeeById(APIGatewayProxyRequestEvent apiGatewayRequest, Context context) {
        initDynamoDB();
        String empId = apiGatewayRequest.getPathParameters().get("empId");
        Employee employee = dynamoDBMapper.load(Employee.class, empId);
        if (employee != null) {
            dynamoDBMapper.delete(employee);
            context.getLogger().log("data deleted successfully :::" + empId);
            return createAPIResponse("data deleted successfully." + empId, 200, Utility.createHeaders());
        } else {
            jsonBody = "Employee Not Found Exception :" + empId;
            return createAPIResponse(jsonBody, 400, Utility.createHeaders());
        }
    }


    private void initDynamoDB() {
        AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard().build();
        dynamoDBMapper = new DynamoDBMapper(client);
    }

    private APIGatewayProxyResponseEvent createAPIResponse(String body, int statusCode, Map<String, String> headers) {
        APIGatewayProxyResponseEvent responseEvent = new APIGatewayProxyResponseEvent();
        responseEvent.setBody(body);
        responseEvent.setHeaders(headers);
        responseEvent.setStatusCode(statusCode);
        return responseEvent;
    }

}
