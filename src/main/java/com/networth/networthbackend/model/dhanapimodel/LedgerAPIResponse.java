package com.networth.networthbackend.model.dhanapimodel;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class LedgerAPIResponse {
    private String narration;

    @JsonFormat(pattern = "MMM dd, yyyy")
    private LocalDate voucherdate;

    private BigDecimal credit;
    private BigDecimal debit;
}
