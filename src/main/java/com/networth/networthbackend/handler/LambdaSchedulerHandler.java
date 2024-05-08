package com.networth.networthbackend.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.networth.networthbackend.service.ChartDataGenerator;

import java.io.IOException;
import java.net.URISyntaxException;

public class LambdaSchedulerHandler implements RequestHandler<Void, Void> {

    ChartDataGenerator chartDataGenerator = new ChartDataGenerator();

    @Override
    public Void handleRequest(Void unused, Context context) {
        try {
            chartDataGenerator.generateChartData();
        } catch (IOException | URISyntaxException | InterruptedException e) {
            throw new RuntimeException(e);
        }
        return unused;
    }
}
