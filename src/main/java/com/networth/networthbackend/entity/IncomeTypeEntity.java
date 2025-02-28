package com.networth.networthbackend.entity;

import lombok.*;

@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class IncomeTypeEntity {

    private Integer id;

    private Integer userId;

    private String name;

    private Boolean isDefault;

}
