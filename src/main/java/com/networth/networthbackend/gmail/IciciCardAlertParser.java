package com.networth.networthbackend.gmail;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ICICI's per-transaction credit card alert. Reads "Available Credit Limit on your card is INR 6,36,015.54
 * and Total Credit Limit is INR 7,50,000.00" and stores the used limit (total - available) negated, since
 * it is money owed. The limits are shared by every ICICI card of the primary holder, so the figure covers
 * all of them. Dated on the transaction date ("on Oct 21, 2026 at 07:59:08") when present, else the day
 * the email arrived (IST); later alerts that day overwrite earlier ones. The purchase itself ("used for a
 * transaction of INR 1,250.00 ... Info: AMAZON" or "INR 1,250.00 spent using ICICI Bank Card XX1234") is
 * returned as the spend.
 */
public class IciciCardAlertParser implements BankBalanceParser {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private static final Pattern AVAILABLE = Pattern.compile(
            "Available\\s+Credit\\s+Limit\\s+on\\s+your\\s+card\\s+is\\s+INR\\s*([\\d,]+(?:\\.\\d{1,2})?)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TOTAL = Pattern.compile(
            "Total\\s+Credit\\s+Limit\\s+is\\s+INR\\s*([\\d,]+(?:\\.\\d{1,2})?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern TXN_DATE = Pattern.compile(
            "\\bon\\s+([A-Za-z]{3,9}\\s+\\d{1,2},\\s*\\d{4})\\s+at\\s+\\d", Pattern.CASE_INSENSITIVE);
    private static final Pattern SPENT = Pattern.compile(
            "(?:transaction\\s+of\\s+INR\\s*([\\d,]+(?:\\.\\d{1,2})?)|INR\\s*([\\d,]+(?:\\.\\d{1,2})?)\\s+spent)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern MERCHANT = Pattern.compile(
            "Info\\s*:\\s*([^\\r\\n<]+?)\\s*(?:\\.\\s|\\.?$|[\\r\\n<])", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
    private static final Pattern CARD_LAST4 = Pattern.compile("\\bXX(\\d{4})\\b", Pattern.CASE_INSENSITIVE);
    private static final DateTimeFormatter SHORT = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter LONG = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH);

    @Override
    public Optional<ParsedBalance> parse(GmailMessage message) {
        if (message.body() == null) return Optional.empty();
        Optional<BigDecimal> available = amount(AVAILABLE, message.body());
        Optional<BigDecimal> total = amount(TOTAL, message.body());
        if (available.isEmpty() || total.isEmpty()) return Optional.empty();
        BigDecimal used = total.get().subtract(available.get());
        return Optional.of(new ParsedBalance(used.negate(), txnDate(message), spend(message)));
    }

    /** Null when the body does not state the amount spent. */
    private static Spend spend(GmailMessage message) {
        Matcher m = SPENT.matcher(message.body());
        if (!m.find()) return null;
        BigDecimal amount;
        try {
            amount = new BigDecimal((m.group(1) != null ? m.group(1) : m.group(2)).replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
        return new Spend(amount, group(MERCHANT, message.body()), group(CARD_LAST4, message.body()));
    }

    private static String group(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text);
        return m.find() ? m.group(1).trim() : null;
    }

    private static LocalDate txnDate(GmailMessage message) {
        Matcher m = TXN_DATE.matcher(message.body());
        if (m.find()) {
            String raw = m.group(1).replaceAll("\\s+", " ").replace(" ,", ",");
            // Title-case the month so "OCT 21, 2026" parses too.
            String text = raw.substring(0, 1).toUpperCase() + raw.substring(1).toLowerCase();
            for (DateTimeFormatter f : new DateTimeFormatter[]{SHORT, LONG}) {
                try {
                    return LocalDate.parse(text, f);
                } catch (DateTimeParseException ignored) {
                    // try the next format
                }
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
