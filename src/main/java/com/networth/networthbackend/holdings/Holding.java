package com.networth.networthbackend.holdings;

import com.networth.networthbackend.common.BaseEntity;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class Holding extends BaseEntity {
    private long accountId;
    private String name;
    private String symbol;
}
