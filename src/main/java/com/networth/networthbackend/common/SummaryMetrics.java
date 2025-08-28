package com.networth.networthbackend.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class SummaryMetrics {
    private BigDecimal currentDayValue;
    private BigDecimal previousDayValue;
    private BigDecimal invested;
}
