package com.networth.networthbackend.chartdata;

import com.networth.networthbackend.common.BaseEntity;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ChartData extends BaseEntity {
    private LocalDate date;
    private BigDecimal value;
    private BigDecimal invested;
    // Display currency the value/invested are expressed in (user's preferred currency).
    private String displayCurrency;
}
