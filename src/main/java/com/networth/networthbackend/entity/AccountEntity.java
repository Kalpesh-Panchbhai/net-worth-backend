package com.networth.networthbackend.entity;

import lombok.*;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class AccountEntity {

    private Integer id;

    private Integer userId;

    private String name;

    private String type;

    private String currency;

    private Boolean active;

    private AccountValueEntity value;
}
