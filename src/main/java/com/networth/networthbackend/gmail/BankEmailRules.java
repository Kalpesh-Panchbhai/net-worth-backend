package com.networth.networthbackend.gmail;

import com.networth.networthbackend.common.Constants;
import lombok.experimental.UtilityClass;

import java.util.List;
import java.util.regex.Pattern;

/**
 * The hardcoded allow-list of bank emails. Every new inbox message is checked against these by
 * headers only; anything that matches no rule is skipped without its body ever being downloaded.
 */
@UtilityClass
public class BankEmailRules {

    public static final List<BankEmailRule> RULES = List.of(
            // "Important : New bill for your Axis Bank card XXXX-2293 is here" -> card account ending 2293
            new BankEmailRule("CRED card bill", "protect@cred.club",
                    Pattern.compile("^Important\\s*:\\s*New bill for your ", Pattern.CASE_INSENSITIVE),
                    new Last4Resolver(Last4Resolver.TYPE_CREDIT_CARD), new CredBillParser()),
            // Monthly password-protected PDF statement -> savings account ending in the PDF's account number
            new BankEmailRule("SBI savings statement", "cbssbi.cas@alerts.sbi.bank.in",
                    Pattern.compile("^E-account statement for your SBI account", Pattern.CASE_INSENSITIVE),
                    new Last4Resolver(Constants.Accounts.TYPE_SAVINGS), PdfStatementParser.sbi()),
            // "ICICI Bank Statement from September 01, 2026 to September 30, 2026 for XXXXXXXX0509" (PDF attached)
            new BankEmailRule("ICICI savings statement", "estatement@icici.bank.in",
                    Pattern.compile("^ICICI Bank Statement from ", Pattern.CASE_INSENSITIVE),
                    new Last4Resolver(Constants.Accounts.TYPE_SAVINGS), PdfStatementParser.icici()),
            // Same ICICI statement also lists the PPF balance -> OTHER account ending in the PPF number.
            // PPF only changes on deposits/yearly interest, so an unchanged balance is not re-recorded.
            new BankEmailRule("ICICI PPF statement", "estatement@icici.bank.in",
                    Pattern.compile("^ICICI Bank Statement from ", Pattern.CASE_INSENSITIVE),
                    new Last4Resolver(Constants.Accounts.TYPE_OTHER), PdfStatementParser.iciciPpf(), true),
            // TEMPORARY: forwarded ICICI statements for end-to-end testing. Remove after testing.
            new BankEmailRule("ICICI savings statement (test fwd)", "ikalpesh10@gmail.com",
                    Pattern.compile("^Fwd:\\s*ICICI Bank Statement from ", Pattern.CASE_INSENSITIVE),
                    new Last4Resolver(Constants.Accounts.TYPE_SAVINGS), PdfStatementParser.icici()),
            new BankEmailRule("ICICI PPF statement (test fwd)", "ikalpesh10@gmail.com",
                    Pattern.compile("^Fwd:\\s*ICICI Bank Statement from ", Pattern.CASE_INSENSITIVE),
                    new Last4Resolver(Constants.Accounts.TYPE_OTHER), PdfStatementParser.iciciPpf(), true),
            // TEMPORARY: forwarded SBI statements for end-to-end testing. Remove after testing.
            new BankEmailRule("SBI savings statement (test fwd)", "ikalpesh10@gmail.com",
                    Pattern.compile("^Fwd:\\s*E-account statement for your SBI account", Pattern.CASE_INSENSITIVE),
                    new Last4Resolver(Constants.Accounts.TYPE_SAVINGS), PdfStatementParser.sbi())
    );

    /** Every rule matching the message's headers (one statement can update several accounts). */
    public static List<BankEmailRule> match(GmailMessage m) {
        return RULES.stream().filter(r -> r.matches(m)).toList();
    }
}
