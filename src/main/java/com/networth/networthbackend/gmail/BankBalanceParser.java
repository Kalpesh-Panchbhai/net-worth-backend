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
     * @param invested     amount invested when the content states it, else null (carried forward)
     * @param spend        the card purchase a transaction alert reports, else null
     * @param payment      the card bill payment a payment email reports, else null; the email states no
     *                     balance, so {@code balance} is null and the payment is added to the latest one
     */
    record ParsedBalance(BigDecimal balance, LocalDate asOf, String accountLast4, BigDecimal invested, Spend spend,
                         BigDecimal payment) {
        public ParsedBalance(BigDecimal balance, LocalDate asOf) {
            this(balance, asOf, null, null, null, null);
        }

        public ParsedBalance(BigDecimal balance, LocalDate asOf, String accountLast4) {
            this(balance, asOf, accountLast4, null, null, null);
        }

        public ParsedBalance(BigDecimal balance, LocalDate asOf, String accountLast4, BigDecimal invested) {
            this(balance, asOf, accountLast4, invested, null, null);
        }

        public ParsedBalance(BigDecimal balance, LocalDate asOf, Spend spend) {
            this(balance, asOf, null, null, spend, null);
        }

        public static ParsedBalance payment(BigDecimal payment, LocalDate asOf) {
            return new ParsedBalance(null, asOf, null, null, null, payment);
        }
    }

    /**
     * One card purchase.
     *
     * @param amount    amount spent, positive
     * @param cardLast4 last 4 digits of the card used when the alert names them, else null
     */
    record Spend(BigDecimal amount, String cardLast4) {
    }

    /** True when the balance is in a PDF attachment, so the sync must download attachments too. */
    default boolean needsPdfs() {
        return false;
    }

    /** Empty when the email matched the rule but carries no balance in a format this parser knows. */
    Optional<ParsedBalance> parse(GmailMessage message);
}
