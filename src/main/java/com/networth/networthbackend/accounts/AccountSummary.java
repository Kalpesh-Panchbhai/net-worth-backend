package com.networth.networthbackend.accounts;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.networth.networthbackend.common.SummaryMetrics;
import lombok.*;
import lombok.experimental.SuperBuilder;

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
    private String currency;
}
