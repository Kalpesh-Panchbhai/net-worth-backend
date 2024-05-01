package com.networth.networthbackend.service;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networth.networthbackend.utility.Utility;

import java.util.List;
import java.util.Map;

public class EmployeeService {
    private static String jsonBody = null;
    private DynamoDBMapper dynamoDBMapper;

//    public void saveEmployee() {
//        initDynamoDB();
//        Employee employee = new Employee(null, "Kalpesh", "kalpesh@gmai.com");
//        dynamoDBMapper.save(employee);
//    }
//
//    public APIGatewayProxyResponseEvent getEmployeeById(APIGatewayProxyRequestEvent apiGatewayRequest, Context context) {
//        initDynamoDB();
//        String empId = apiGatewayRequest.getPathParameters().get("empId");
//        Employee employee = dynamoDBMapper.load(Employee.class, empId);
//        if (employee != null) {
////            jsonBody = Utility.convertObjToString(employee, context);
//            context.getLogger().log("fetch employee By ID:::" + jsonBody);
//            return createAPIResponse(jsonBody, 200, Utility.createHeaders());
//        } else {
//            jsonBody = "Employee Not Found Exception :" + empId;
//            return createAPIResponse(jsonBody, 400, Utility.createHeaders());
//        }
//
//    }
//
//    public APIGatewayProxyResponseEvent getEmployees(APIGatewayProxyRequestEvent apiGatewayRequest, Context context) {
//        initDynamoDB();
//        List<Employee> employees = dynamoDBMapper.scan(Employee.class, new DynamoDBScanExpression());
////        jsonBody = Utility.convertListOfObjToString(employees, context);
//        context.getLogger().log("fetch employee List:::" + jsonBody);
//        return createAPIResponse(jsonBody, 200, Utility.createHeaders());
//    }

//    public APIGatewayProxyResponseEvent deleteEmployeeById(APIGatewayProxyRequestEvent apiGatewayRequest, Context context) {
//        initDynamoDB();
//        String empId = apiGatewayRequest.getPathParameters().get("empId");
//        Employee employee = dynamoDBMapper.load(Employee.class, empId);
//        if (employee != null) {
//            dynamoDBMapper.delete(employee);
//            context.getLogger().log("data deleted successfully :::" + empId);
//            return createAPIResponse("data deleted successfully." + empId, 200, Utility.createHeaders());
//        } else {
//            jsonBody = "Employee Not Found Exception :" + empId;
//            return createAPIResponse(jsonBody, 400, Utility.createHeaders());
//        }
//    }

}
