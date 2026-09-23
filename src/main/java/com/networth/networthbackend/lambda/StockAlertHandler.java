package com.networth.networthbackend.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.networth.networthbackend.stock.StockAlertService;

/**
 * Scheduled (every few minutes) entry point that fires due Slack stock alerts. Kept separate from
 * the heavy daily data job so alerts can honour the user's per-market configured time without
 * re-running the signal scan.
 */
public class StockAlertHandler implements RequestHandler<Object, String> {
    @Override
    public String handleRequest(Object input, Context context) {
        new StockAlertService().dispatchDue();
        return "ok";
    }
}
