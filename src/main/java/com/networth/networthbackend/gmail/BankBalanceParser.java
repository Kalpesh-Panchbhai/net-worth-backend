package com.networth.networthbackend.gmail;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

/** Pulls the account balance out of one bank's email. One implementation per email format. */
public interface BankBalanceParser {

    /**
     * @param balance the balance in the account's own currency
     * @param asOf    the date the balance applies to
     */
    record ParsedBalance(BigDecimal balance, LocalDate asOf) {
    }

    /** Empty when the email matched the rule but carries no balance in a format this parser knows. */
    Optional<ParsedBalance> parse(GmailMessage message);
}
