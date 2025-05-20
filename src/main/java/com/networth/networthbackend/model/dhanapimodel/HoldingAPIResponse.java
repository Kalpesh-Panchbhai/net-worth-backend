package com.networth.networthbackend.model.dhanapimodel;

import lombok.Data;

@Data
public class HoldingAPIResponse {

    private String exchange;
    private String tradingSymbol;
    private String securityId;
    private String isin;
    private Integer totalQty;
    private Integer dpQty;
    private Integer t1Qty;
    private Integer mtf_t1_qty;
    private Integer mtf_qty;
    private Integer availableQty;
    private Integer collateralQty;
    private Double avgCostPrice;
    private Double lastTradedPrice;

}
