package com.networth.networthbackend.gmail;

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
                    new CardLast4Resolver(), new CredBillParser())
    );

    /** First rule matching the message's headers, or null. */
    public static BankEmailRule match(GmailMessage m) {
        for (BankEmailRule r : RULES) {
            if (r.matches(m)) return r;
        }
        return null;
    }
}
