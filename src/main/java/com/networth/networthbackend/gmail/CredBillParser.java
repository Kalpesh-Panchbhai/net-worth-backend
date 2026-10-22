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
 * CRED's "New bill for your ... card" email. Reads "total amount due ₹63,835.00" and stores it
 * negated, since a card bill is money owed. Dated on "bill generated on Oct 20, 2026" when present,
 * else the day the email arrived (IST).
 */
public class CredBillParser implements BankBalanceParser {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    // The rupee sign may arrive as the character or as an HTML entity once tags are stripped.
    private static final Pattern TOTAL_DUE = Pattern.compile(
            "total\\s+amount\\s+due\\s*(?:₹|&#8377;|&#x20b9;|Rs\\.?|INR)?\\s*([\\d,]+(?:\\.\\d{1,2})?)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern BILL_DATE = Pattern.compile(
            "bill\\s+generated\\s+on\\s*([A-Za-z]{3,9}\\s+\\d{1,2},\\s*\\d{4})", Pattern.CASE_INSENSITIVE);
    private static final DateTimeFormatter SHORT = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter LONG = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH);

    @Override
    public Optional<ParsedBalance> parse(GmailMessage message) {
        if (message.body() == null) return Optional.empty();
        Matcher m = TOTAL_DUE.matcher(message.body());
        if (!m.find()) return Optional.empty();
        BigDecimal due;
        try {
            due = new BigDecimal(m.group(1).replace(",", ""));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
        return Optional.of(new ParsedBalance(due.negate(), billDate(message)));
    }

    private static LocalDate billDate(GmailMessage message) {
        Matcher m = BILL_DATE.matcher(message.body());
        if (m.find()) {
            String raw = m.group(1).replaceAll("\\s+", " ").replace(" ,", ",");
            // Title-case the month so "OCT 20, 2026" parses too.
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
}
