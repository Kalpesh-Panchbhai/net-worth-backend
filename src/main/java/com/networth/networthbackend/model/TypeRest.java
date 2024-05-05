package com.networth.networthbackend.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TypeRest {

    private String id;

    private String userId;

    private String name;

    private Boolean isDefault;
}
