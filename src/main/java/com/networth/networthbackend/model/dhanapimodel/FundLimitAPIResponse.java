package com.networth.networthbackend.model.dhanapimodel;

import lombok.Data;

@Data
public class FundLimitAPIResponse {

    private String dhanClientId;
    private Double availabelBalance;
    private Double sodLimit;
    private Double collateralAmount;
    private Double receiveableAmount;
    private Double utilizedAmount;
    private Double blockedPayoutAmount;
    private Double withdrawableBalance;

}
