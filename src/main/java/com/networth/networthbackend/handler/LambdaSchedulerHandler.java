package com.networth.networthbackend.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.networth.networthbackend.service.ChartService;
import lombok.SneakyThrows;

public class LambdaSchedulerHandler implements RequestHandler<Void, Void> {

    @SneakyThrows
    @Override
    public Void handleRequest(Void unused, Context context) {
        ChartService chartService = new ChartService();
        chartService.generateData();
        chartService.repository.databaseService.disconnect();
        return unused;
    }
}
