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
    /**
     * Money-weighted return (as a fraction, 0.12 == 12%) from inception up to this point's date:
     * the IRR of every cash flow on or before {@code date}, closed with this point's {@code value}.
     * Null where it is not meaningful (before the first investment, no sign change) or does not
     * apply to the entity. Lets the client show "XIRR until this date" on hover.
     */
    private Double xirr;
}
