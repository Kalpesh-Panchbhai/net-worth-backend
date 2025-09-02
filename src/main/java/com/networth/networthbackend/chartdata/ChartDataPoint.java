package com.networth.networthbackend.chartdata;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChartDataPoint {
    private Long id;
    private LocalDate date;
    private BigDecimal currentDayValue;
    private BigDecimal invested;
}
