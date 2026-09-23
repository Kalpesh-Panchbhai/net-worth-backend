package com.stock.analyzer.backtest.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PendingOrder {
    private String id;
    private Direction direction;
    @Builder.Default
    private OrderType orderType = OrderType.MARKET;
    private Double limitPrice;
    private Double stopPrice;
    private Double stopLoss;
    private Double takeProfit;
    private Double qty;
    private Double qtyPercent;
    @Builder.Default
    private String comment = "";
    @Builder.Default
    private int barIndex = 0;
}
