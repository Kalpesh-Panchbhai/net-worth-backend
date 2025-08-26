package com.networth.networthbackend.incomes;

import com.networth.networthbackend.common.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class Income extends BaseEntity {
    private long userId;
    private long incomeSourceId;
    private long incomeTagId;
    private BigDecimal netAmount;
    private BigDecimal taxPaid;
    private String currency;
    private Instant creditedDate; // UTC
}
