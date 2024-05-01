package com.networth.networthbackend.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.networth.networthbackend.service.EmployeeService;

public class LambdaSchedulerHandler implements RequestHandler<Void, Void> {

    @Override
    public Void handleRequest(Void unused, Context context) {
//        EmployeeService employeeService = new EmployeeService();
//        employeeService.saveEmployee();
        return null;
    }
}
