package com.networth.networthbackend.gmail;

import lombok.experimental.UtilityClass;

import java.util.List;

/**
 * The hardcoded allow-list of bank emails. Every new inbox message is checked against these by
 * headers only; anything that matches no rule is skipped without its body ever being downloaded.
 */
@UtilityClass
public class BankEmailRules {

    // Example — fill in the real sender, subject, account/holding ids and balance regex per bank:
    // new BankEmailRule("HDFC savings", "alerts@hdfcbank.net", java.util.regex.Pattern.compile("(?i)account update"),
    //         12L, 34L, new RegexBalanceParser("Avl\\s*Bal[:\\s]*(?:INR|Rs\\.?)\\s*([\\d,]+(?:\\.\\d{1,2})?)")),
    public static final List<BankEmailRule> RULES = List.of(
    );

    /** First rule matching the message's headers, or null. */
    public static BankEmailRule match(GmailMessage m) {
        for (BankEmailRule r : RULES) {
            if (r.matches(m)) return r;
        }
        return null;
    }
}
