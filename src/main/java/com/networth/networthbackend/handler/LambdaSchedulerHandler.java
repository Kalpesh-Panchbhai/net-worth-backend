package com.networth.networthbackend.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

public class LambdaSchedulerHandler implements RequestHandler<Void, Void> {

    @Override
    public Void handleRequest(Void unused, Context context) {
        return unused;
    }
}
