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

    // Display-only amounts converted at the credited date's FX rate. Populated on read, not
    // persisted. `convertedCurrency` is always set: it names the currency the converted* fields
    // are in, which is the user's preferred currency, or the income's own currency when no rate
    // could be resolved. It is never null, so a client never has to guess.
    private BigDecimal convertedNetAmount;
    private BigDecimal convertedTaxPaid;
    // net + tax, converted. Sent so a client can total gross without re-deriving it.
    private BigDecimal convertedGrossAmount;
    private String convertedCurrency;
}
