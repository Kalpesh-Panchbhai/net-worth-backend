package com.networth.networthbackend.gmail;

import java.util.regex.Pattern;

/**
 * Which emails count as a balance update for one bank account, and where the balance goes.
 *
 * @param name      label used in logs and Slack, e.g. "HDFC savings"
 * @param sender    exact sender address (not the display name, which anyone can spoof)
 * @param subject   must be found somewhere in the subject
 * @param account   picks the account/holding the balance goes to
 * @param parser    extracts the balance from the email body
 */
public record BankEmailRule(String name, String sender, Pattern subject, AccountResolver account,
                            BankBalanceParser parser) {

    public boolean matches(GmailMessage m) {
        return sender.equalsIgnoreCase(m.fromAddress())
                && m.subject() != null
                && subject.matcher(m.subject()).find();
    }
}
