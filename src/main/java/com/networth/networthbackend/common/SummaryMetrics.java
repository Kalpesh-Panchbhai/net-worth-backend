package com.networth.networthbackend.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

/**
 * Monetary rollup shared by account, holding and watchlist summaries.
 * <p>
 * Every amount here is expressed in {@link #displayCurrency} — the user's preferred currency —
 * and {@code displayCurrency} is always populated on read responses. {@link #gain} and
 * {@link #dayChange} are derived server-side rather than left to the client, so a single response
 * can never mix two conversion conventions or two currency labels for the same money.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class SummaryMetrics extends BaseEntity {
    private BigDecimal currentDayValue;
    private BigDecimal previousDayValue;
    private BigDecimal invested;
    /** {@code currentDayValue - invested}. */
    private BigDecimal gain;
    /** {@code currentDayValue - previousDayValue}. */
    private BigDecimal dayChange;
    /** Money-weighted annualised return as a fraction (0.12 == 12%); null when not computable. */
    private Double xirr;
    /** ISO code the monetary fields above are expressed in. Always set on read responses. */
    private String displayCurrency;
}
