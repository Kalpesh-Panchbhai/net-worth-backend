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
 * ICICI's credit card payment confirmation: "We have received payment of INR 2,277.93 on your ICICI Bank
 * Credit Card account 4035 XXXX XXXX 6004 on 29-Oct-2026." The email states no limits, so it returns only
 * the payment; the sync adds it to the card's latest balance. Dated on the stated payment date when
 * present, else the day the email arrived (IST).
 */
public class IciciCardPaymentParser implements BankBalanceParser {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private static final Pattern PAYMENT = Pattern.compile(
            "received\\s+payment\\s+of\\s+INR\\s*([\\d,]+(?:\\.\\d{1,2})?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PAID_ON = Pattern.compile(
            "\\bon\\s+(\\d{1,2}-[A-Za-z]{3}-\\d{4})", Pattern.CASE_INSENSITIVE);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d-MMM-yyyy", Locale.ENGLISH);

    @Override
    public Optional<ParsedBalance> parse(GmailMessage message) {
        if (message.body() == null) return Optional.empty();
        // HTML bodies may pad values with &nbsp;.
        String text = message.body().replace("&nbsp;", " ").replace(' ', ' ');
        Matcher m = PAYMENT.matcher(text);
        if (!m.find()) return Optional.empty();
        BigDecimal amount;
        try {
            amount = new BigDecimal(m.group(1).replace(",", ""));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
        if (amount.signum() <= 0) return Optional.empty();
        return Optional.of(ParsedBalance.payment(amount, paidOn(text, message)));
    }

    private static LocalDate paidOn(String text, GmailMessage message) {
        Matcher m = PAID_ON.matcher(text);
        if (m.find()) {
            String raw = m.group(1);
            // Title-case the month so "29-OCT-2026" parses too.
            int i = raw.indexOf('-') + 1;
            String normalized = raw.substring(0, i) + raw.substring(i, i + 1).toUpperCase() + raw.substring(i + 1).toLowerCase();
            try {
                return LocalDate.parse(normalized, DATE);
            } catch (DateTimeParseException ignored) {
                // fall back to the arrival date
            }
        }
        return LocalDate.ofInstant(message.receivedAt(), IST);
    }
}
