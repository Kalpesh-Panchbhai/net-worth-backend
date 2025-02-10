package com.networth.networthbackend.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class ChartDataEntity {

    private String id;

    private String userId;

    private String accountId;

    private String date;

    private Double value;
}
