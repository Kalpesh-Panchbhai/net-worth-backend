package com.networth.networthbackend.model.dhanapimodel;

import lombok.Data;

@Data
public class PositionAPIResponse {

    private String dhanClientId;
    private String tradingSymbol;
    private String securityId;
    private String positionType;
    private String exchangeSegment;
    private String productType;
    private Double buyAvg;
    private Double costPrice;
    private Integer buyQty;
    private Double sellAvg;
    private Integer sellQty;
    private Integer netQty;
    private Double realizedProfit;
    private Double unrealizedProfit;
    private Double rbiReferenceRate;
    private Integer multiplier;
    private Integer carryForwardBuyQty;
    private Integer carryForwardSellQty;
    private Double carryForwardBuyValue;
    private Double carryForwardSellValue;
    private Integer dayBuyQty;
    private Integer daySellQty;
    private Double dayBuyValue;
    private Double daySellValue;
    private String drvExpiryDate;
    private String drvOptionType;
    private Double drvStrikePrice;
    private Boolean crossCurrency;

}
