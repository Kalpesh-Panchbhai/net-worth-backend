package com.networth.networthbackend.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.networth.networthbackend.stock.StockAlertService;

import java.util.Map;

/**
 * Entry point for Slack stock alerts. Each market has its own EventBridge rule that fires this at
 * the configured time with a constant input {@code {"market":"in"|"us"}} — so alert timing lives in
 * the rule (readable/updatable from the UI) rather than a fixed poll. If invoked without a market
 * (legacy), it falls back to the window-based dispatch of all markets.
 */
public class StockAlertHandler implements RequestHandler<Object, String> {
    @Override
    public String handleRequest(Object input, Context context) {
        StockAlertService service = new StockAlertService();
        String market = input instanceof Map<?, ?> m && m.get("market") != null ? String.valueOf(m.get("market")) : null;
        if (market != null && !market.isBlank()) {
            service.dispatchMarket(market);
            return "dispatched:" + market;
        }
        service.dispatchDue();
        return "ok";
    }
}
