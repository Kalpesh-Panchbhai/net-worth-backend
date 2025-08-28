package com.networth.networthbackend.accounts;

import com.networth.networthbackend.common.BaseEntity;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class Account extends BaseEntity {
    private long userId;
    private String name;
    private String type;
    private boolean isActive;
    private String currency;
}
