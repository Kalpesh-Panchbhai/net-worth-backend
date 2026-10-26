package com.networth.networthbackend.gmail;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Axis Bank's per-transaction credit card alert, in either of its layouts: "Available limit (INR): 93262.71"
 * / "Total credit limit (INR): 157000", or "Available Limit*: INR 88964.46" / "Total Credit Limit*: INR 157000".
 * Stores the used limit (total - available) negated, since it is money owed. The limits are shared by
 * every Axis card of the holder, so the figure covers all of them. Dated on "Date and time: 15-10-2026" /
 * "Date &amp; Time: 21-10-2026" when present, else the day the email arrived (IST); later alerts that day
 * overwrite earlier ones. The purchase itself ("INR 105 spent on credit card no. XX6769" in the subject, or
 * "Transaction amount (INR): INR 115868.52" in the body) is returned as the spend.
 */
public class AxisCardAlertParser implements BankBalanceParser {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private static final Pattern AVAILABLE = Pattern.compile(
            "Available\\s+limit\\s*\\*?\\s*(?:\\(INR\\))?\\s*:\\s*(?:INR\\s*)?([\\d,]+(?:\\.\\d{1,2})?)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TOTAL = Pattern.compile(
            "Total\\s+credit\\s+limit\\s*\\*?\\s*(?:\\(INR\\))?\\s*:\\s*(?:INR\\s*)?([\\d,]+(?:\\.\\d{1,2})?)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TXN_DATE = Pattern.compile(
            "Date\\s*(?:and|&|&amp;)\\s*time\\s*:\\s*(\\d{2}-\\d{2}-\\d{4})", Pattern.CASE_INSENSITIVE);
    private static final Pattern SPENT = Pattern.compile(
            "(?:INR\\s*([\\d,]+(?:\\.\\d{1,2})?)\\s+spent|Transaction\\s+Amount\\s*(?:\\(INR\\))?\\s*:?\\s*(?:INR\\s*)?([\\d,]+(?:\\.\\d{1,2})?))",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CARD_LAST4 = Pattern.compile("\\bXX(\\d{4})\\b", Pattern.CASE_INSENSITIVE);
    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    @Override
    public Optional<ParsedBalance> parse(GmailMessage message) {
        if (message.body() == null) return Optional.empty();
        Optional<BigDecimal> available = amount(AVAILABLE, message.body());
        Optional<BigDecimal> total = amount(TOTAL, message.body());
        if (available.isEmpty() || total.isEmpty()) return Optional.empty();
        BigDecimal used = total.get().subtract(available.get());
        return Optional.of(new ParsedBalance(used.negate(), txnDate(message), spend(message)));
    }

    /** Null when neither the subject nor the body states the amount spent. */
    private static Spend spend(GmailMessage message) {
        // HTML bodies pad values with &nbsp; ("Transaction amount (INR):&nbsp; INR 115868.52").
        String text = ((message.subject() == null ? "" : message.subject()) + "\n" + message.body())
                .replace("&nbsp;", " ").replace('\u00a0', ' ');
        Matcher m = SPENT.matcher(text);
        if (!m.find()) return null;
        BigDecimal amount;
        try {
            amount = new BigDecimal((m.group(1) != null ? m.group(1) : m.group(2)).replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
        return new Spend(amount, group(CARD_LAST4, text));
    }

    private static String group(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text);
        return m.find() ? m.group(1).trim() : null;
    }

    private static LocalDate txnDate(GmailMessage message) {
        Matcher m = TXN_DATE.matcher(message.body());
        if (m.find()) {
            try {
                return LocalDate.parse(m.group(1), DMY);
            } catch (DateTimeParseException ignored) {
                // fall back to the arrival date
            }
        }
        return LocalDate.ofInstant(message.receivedAt(), IST);
    }

    private static Optional<BigDecimal> amount(Pattern pattern, String body) {
        Matcher m = pattern.matcher(body);
        if (!m.find()) return Optional.empty();
        try {
            return Optional.of(new BigDecimal(m.group(1).replace(",", "")));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
