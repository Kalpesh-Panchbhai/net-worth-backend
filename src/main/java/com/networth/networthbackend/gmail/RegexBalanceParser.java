package com.networth.networthbackend.gmail;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser for emails that state the balance in the body text, e.g. "Avl Bal: INR 1,23,456.78".
 * Group 1 of the pattern must capture the amount; commas are stripped, so Indian grouping works.
 * The balance is dated on the day the email arrived, in IST.
 */
public class RegexBalanceParser implements BankBalanceParser {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final Pattern pattern;

    public RegexBalanceParser(String regex) {
        this.pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
    }

    @Override
    public Optional<ParsedBalance> parse(GmailMessage message) {
        if (message.body() == null) return Optional.empty();
        Matcher m = pattern.matcher(message.body());
        if (!m.find()) return Optional.empty();
        try {
            BigDecimal balance = new BigDecimal(m.group(1).replace(",", "").trim());
            LocalDate asOf = LocalDate.ofInstant(message.receivedAt(), IST);
            return Optional.of(new ParsedBalance(balance, asOf));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
