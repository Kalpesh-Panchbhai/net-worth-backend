package com.networth.networthbackend.strategy;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SupertrendResponse {

    private String symbolName;
    private String symbol;
    private Double supertrendValue;
    private Double potentialProfit = 0.0;
    private Boolean shouldBuy;
    private Boolean hasError = false;
    private String errorMessage;
}
