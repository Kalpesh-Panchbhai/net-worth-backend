package com.networth.networthbackend.entity;

import lombok.*;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class WatchAccountEntity {

    private Integer id;

    private Integer accountId;

    private Integer watchId;

    private WatchEntity watch;

    private AccountEntity account;
}
