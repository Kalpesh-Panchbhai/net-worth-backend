package com.networth.networthbackend.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.networth.networthbackend.tools.DailyDataRunner;


public class DailyDataHandler implements RequestHandler<Void, Void> {
    @Override
    public Void handleRequest(Void unused, Context context) {
        try {
            DailyDataRunner.run();
            return unused;
        } catch (Exception e) {
            return unused;
        }
    }
}
