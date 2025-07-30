package com.networth.networthbackend.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.networth.networthbackend.service.ChartService;
import lombok.SneakyThrows;

public class LambdaSchedulerDhanAccountHandler implements RequestHandler<Void, Void> {

    @SneakyThrows
    @Override
    public Void handleRequest(Void unused, Context context) {
        ChartService chartService = new ChartService(context);
        chartService.generateDhanNonBrokerAccountData(17);
        chartService.repository.databaseService.disconnect();
        return unused;
    }
}
