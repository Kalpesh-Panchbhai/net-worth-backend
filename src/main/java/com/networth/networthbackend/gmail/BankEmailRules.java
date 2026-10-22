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
            // Every ICICI card alert states the limit shared by all the holder's cards -> one combined card account
            new BankEmailRule("ICICI card alert", "credit_cards@icici.bank.in",
                    Pattern.compile("^Transaction alert for your ICICI Bank Credit Card", Pattern.CASE_INSENSITIVE),
                    new NameResolver(Last4Resolver.TYPE_CREDIT_CARD, "ICICI Bank"), new IciciCardAlertParser()),
            // TEMP: same ICICI alert forwarded from a personal address for testing; remove after verifying
            new BankEmailRule("ICICI card alert (test)", "ikalpesh10@gmail.com",
                    Pattern.compile("Transaction alert for your ICICI Bank Credit Card", Pattern.CASE_INSENSITIVE),
                    new NameResolver(Last4Resolver.TYPE_CREDIT_CARD, "ICICI Bank"), new IciciCardAlertParser()),
            // "INR 105 spent on credit card no. XX6769" or "Transaction alert on Axis Bank Credit Card"; the limit
            // is shared by all the holder's Axis cards -> one combined card account
            new BankEmailRule("Axis card alert", "alerts@axis.bank.in",
                    Pattern.compile("^(INR\\s+[\\d,.]+\\s+spent on credit card no\\.|Transaction alert on Axis Bank Credit Card)",
                            Pattern.CASE_INSENSITIVE),
                    new NameResolver(Last4Resolver.TYPE_CREDIT_CARD, "Axis Bank"), new AxisCardAlertParser()),
            // TEMP: same Axis alert forwarded from a personal address for testing; remove after verifying
            new BankEmailRule("Axis card alert (test)", "ikalpesh10@gmail.com",
                    Pattern.compile("(INR\\s+[\\d,.]+\\s+spent on credit card no\\.|Transaction alert on Axis Bank Credit Card)",
                            Pattern.CASE_INSENSITIVE),
                    new NameResolver(Last4Resolver.TYPE_CREDIT_CARD, "Axis Bank"), new AxisCardAlertParser()),
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
            // NPS statement PDF -> OTHER account ending in the PRAN; contribution is invested, valuation is value
            new BankEmailRule("NPS statement", "kcra@kfintech.com",
                    Pattern.compile("^Monthly Transaction Statement for your NPS account", Pattern.CASE_INSENSITIVE),
                    new Last4Resolver(Constants.Accounts.TYPE_OTHER), new NpsStatementParser())
    );

    /** Every rule matching the message's headers (one statement can update several accounts). */
    public static List<BankEmailRule> match(GmailMessage m) {
        return RULES.stream().filter(r -> r.matches(m)).toList();
    }
}
