package com.networth.networthbackend.holdings;

import com.networth.networthbackend.common.SummaryMetrics;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class HoldingSummary extends SummaryMetrics {
    private long accountId;
    private String name;
    private String symbol;
    /**
     * The latest transaction's {@code value} for this holding. It is a share/unit count only for
     * broker accounts; for every other account type that column stores a money balance, in which
     * case this field is money and is converted into {@code displayCurrency} like every other
     * amount here. {@link #unitsAreShares} says which of the two it is, so a client never renders a
     * unit count as money — or formats a balance with the wrong currency symbol.
     */
    private BigDecimal units;
    @com.fasterxml.jackson.annotation.JsonProperty("unitsAreShares")
    private boolean unitsAreShares;
}
