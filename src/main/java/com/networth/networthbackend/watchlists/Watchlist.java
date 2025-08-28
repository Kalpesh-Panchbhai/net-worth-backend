package com.networth.networthbackend.watchlists;

import com.networth.networthbackend.common.BaseEntity;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class Watchlist extends BaseEntity {
    private long userId;
    private String name;
}
