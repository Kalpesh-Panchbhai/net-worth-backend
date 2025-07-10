package com.networth.networthbackend.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.networth.networthbackend.strategy.SupertrendService;
import lombok.SneakyThrows;

public class LambdaSchedulerSuperTrendTelegramBotHandler implements RequestHandler<Void, Void> {

    @SneakyThrows
    @Override
    public Void handleRequest(Void unused, Context context) {
        new SupertrendService().execute();
        return unused;
    }
}
