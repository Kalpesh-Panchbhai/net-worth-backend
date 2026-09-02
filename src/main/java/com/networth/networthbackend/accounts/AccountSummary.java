package com.networth.networthbackend.accounts;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.networth.networthbackend.common.CashFlow;
import com.networth.networthbackend.common.SummaryMetrics;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class AccountSummary extends SummaryMetrics {
    private long userId;
    private String name;
    private String type;
    @JsonProperty("isActive")
    private boolean isActive;
    @JsonProperty("needsDailyData")
    private boolean needsDailyData;
    /** Native currency of the account. The monetary fields are in {@code displayCurrency}. */
    private String currency;
    /**
     * This account's dated cash flows in {@code displayCurrency}, so a client can pool an
     * arbitrary subset of accounts and get that group's exact XIRR. Empty for accounts XIRR does
     * not apply to. See {@link CashFlow}.
     */
    private List<CashFlow> cashFlows;
}
