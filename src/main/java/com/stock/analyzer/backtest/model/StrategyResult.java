package com.stock.analyzer.backtest.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class StrategyResult {
    private String strategyName;
    private String timeframe;
    private ScoreData scoreData;
}
