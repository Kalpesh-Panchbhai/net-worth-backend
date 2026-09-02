package com.networth.networthbackend.transactions;

import com.networth.networthbackend.common.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class Transaction extends BaseEntity {
    private long accountId;
    private long holdingId;
    private LocalDate txnDate;
    // Cumulative for the holding as of txnDate. On read responses this is the running sum of the
    // converted increments below, so it always equals the sum of the deltas up to this row.
    private BigDecimal invested;
    private BigDecimal value;
    // This row's own change, converted at this row's date. Sent so the client never has to
    // subtract two cumulative amounts that were translated at different dates — the FX drift
    // between those dates would end up inside the difference.
    private BigDecimal investedDelta;
    private BigDecimal valueDelta;
    // ISO code the money fields are expressed in. Always set on read responses; on write
    // responses it is the account's native currency, because the echoed row is what was stored.
    private String displayCurrency;
    // True when `value` holds units rather than money, so it is never FX-converted. Sent
    // explicitly so the client does not have to re-derive it from the account type.
    @com.fasterxml.jackson.annotation.JsonProperty("valueInUnits")
    private boolean valueInUnits;
}
