package com.networth.networthbackend.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class BalanceDetailRest {

    private String dateTime;

    private Double value = 0.0;
}
