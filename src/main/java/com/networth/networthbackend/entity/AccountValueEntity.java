package com.networth.networthbackend.entity;

import lombok.*;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class AccountValueEntity {

    private Double currentValue;

    private Double previousValue;
}
