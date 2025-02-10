package com.networth.networthbackend.entity;

import com.networth.networthbackend.model.BalanceRest;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class AccountBalanceEntity {

    private String id;

    private BalanceRest balance;
}
