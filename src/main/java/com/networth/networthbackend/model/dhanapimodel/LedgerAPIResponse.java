package com.networth.networthbackend.model.dhanapimodel;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class LedgerAPIResponse {
    private String narration;
    @JsonFormat(pattern = "MMM dd, yyyy")
    private LocalDate voucherdate;
    private BigDecimal debit;
    private BigDecimal credit;
}
