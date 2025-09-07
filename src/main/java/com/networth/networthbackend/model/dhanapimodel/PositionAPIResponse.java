package com.networth.networthbackend.model.dhanapimodel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PositionAPIResponse {
    private double netQty;
    private double realizedProfit;
    private double unrealizedProfit;
}
