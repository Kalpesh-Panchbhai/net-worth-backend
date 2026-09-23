package com.stock.analyzer.backtest.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class Asset {
    private String symbol;
    private String name;
    private String exchange;
    private String currency;
    private String industry;
}
