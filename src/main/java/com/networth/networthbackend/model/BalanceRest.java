package com.networth.networthbackend.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BalanceRest {

    private BalanceDetailRest currentDayValue;

    private BalanceDetailRest previousDayValue;
}
