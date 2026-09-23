package com.stock.analyzer.backtest.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Bar {
    private LocalDate date;
    private double open;
    private double high;
    private double low;
    private double close;
    private double volume;
}
