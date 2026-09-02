package com.networth.networthbackend.incomes;

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
public class Income extends BaseEntity {
    private long userId;
    private long incomeSourceId;
    private long incomeTagId;
    private BigDecimal netAmount;
    private BigDecimal taxPaid;
    private String currency;
    private LocalDate creditedDate; // date-only

    // Display-only values converted to the user's preferred currency at the credited date.
    // Populated on read; not persisted.
    private BigDecimal convertedNetAmount;
    private BigDecimal convertedTaxPaid;
    private String convertedCurrency;
}
