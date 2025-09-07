package com.networth.networthbackend.model.dhanapimodel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class HoldingAPIResponse {
    private double availableQty;
    private double lastTradedPrice;
}
