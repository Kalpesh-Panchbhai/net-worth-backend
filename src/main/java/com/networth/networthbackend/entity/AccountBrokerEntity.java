package com.networth.networthbackend.entity;

import lombok.*;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class AccountBrokerEntity {

    private Integer id;

    private Integer accountId;

    private String symbol;

    private String name;

    private AccountValueEntity value;
}
