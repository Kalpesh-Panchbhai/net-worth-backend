package com.networth.networthbackend.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.networth.networthbackend.tools.DailyDataRunner;

import java.util.HashMap;
import java.util.Map;

import static com.networth.networthbackend.common.Constants.DailyDataRunner.*;

public class DailyDataHandler implements RequestHandler<Void, Void> {
    @Override
    public Void handleRequest(Void unused, Context context) {
        try {
            Map<String, String> params = new HashMap<>();
            params.put(PARAM_BATCH_SIZE, DEFAULT_BATCH_SIZE);
            params.put(PARAM_MAX_ACCOUNTS, DEFAULT_MAX_ACCOUNTS);
            params.put(PARAM_MODE, MODE_7D);
            DailyDataRunner.run(params);
            return unused;
        } catch (Exception e) {
            return unused;
        }
    }
}
