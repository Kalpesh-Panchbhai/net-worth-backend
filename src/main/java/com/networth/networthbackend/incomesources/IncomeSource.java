package com.networth.networthbackend.incomesources;

import com.networth.networthbackend.common.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class IncomeSource extends BaseEntity {
    private long userId;
    private String name;
    private boolean isDefault;
}
