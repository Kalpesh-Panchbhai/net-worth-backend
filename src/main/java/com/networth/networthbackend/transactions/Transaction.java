package com.networth.networthbackend.transactions;

import com.networth.networthbackend.common.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class Transaction extends BaseEntity {
    private long accountId;
    private long holdingId;
    private LocalDate txnDate;
    private BigDecimal invested;
    private BigDecimal value;
}
