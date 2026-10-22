package com.networth.networthbackend.gmail;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

/** Pulls the account balance out of one bank's email. One implementation per email format. */
public interface BankBalanceParser {

    /**
     * @param balance the balance in the account's own currency
     * @param asOf         the date the balance applies to
     * @param accountLast4 last 4 digits of the account number when the content names it, else null
     */
    record ParsedBalance(BigDecimal balance, LocalDate asOf, String accountLast4) {
        public ParsedBalance(BigDecimal balance, LocalDate asOf) {
            this(balance, asOf, null);
        }
    }

    /** True when the balance is in a PDF attachment, so the sync must download attachments too. */
    default boolean needsPdfs() {
        return false;
    }

    /** Empty when the email matched the rule but carries no balance in a format this parser knows. */
    Optional<ParsedBalance> parse(GmailMessage message);
}
