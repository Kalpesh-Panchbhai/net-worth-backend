package com.networth.networthbackend.model.dhanapimodel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class FundLimitAPIResponse {
    private double availabelBalance; // Note: API has typo "availabel" instead of "available"
    private double utilizedAmount;
}
