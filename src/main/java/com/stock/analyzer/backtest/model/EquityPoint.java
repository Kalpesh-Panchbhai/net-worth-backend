package com.stock.analyzer.backtest.model;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDate;

@Data
@AllArgsConstructor
public class EquityPoint {
    private LocalDate date;
    private double equity;
    private double drawdownPct;
}
