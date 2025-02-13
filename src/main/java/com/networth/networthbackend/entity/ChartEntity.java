package com.networth.networthbackend.entity;

import lombok.*;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class ChartEntity {

    private Integer id;

    private Integer accountId;

    private Integer accountBrokerId;

    private String date;

    private Double value;
}
