package com.networth.networthbackend.gmail;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;
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
 * Parser for a bank's password-protected PDF statement attachment. The balance pattern must capture
 * the account's last 4 digits in group 1 and the balance in group 2; commas are stripped, so Indian
 * grouping works. The statement date pattern's group 1 is parsed with the given format; if absent
 * the balance is dated on the day the email arrived (IST). The PDF password is read from env.
 */
@Slf4j
public class PdfStatementParser implements BankBalanceParser {

    /** SBI: "P SINGLE XXXXXXX1225 OPEN INR 8665.38 0.00 0.00 8665.38", "As on 30-09-26". */
    public static PdfStatementParser sbi() {
        return new PdfStatementParser("SBI_STATEMENT_PASSWORD",
                "X{3,}(\\d{4})\\s+OPEN\\s+INR\\s+(-?[\\d,]+(?:\\.\\d{1,2})?)",
                "As\\s+on\\s+(\\d{2}-\\d{2}-\\d{2})", "dd-MM-yy");
    }

    /** ICICI: "Savings A/c XXXXXXXX0509 4,31,104.93", "as on September 30, 2026". */
    public static PdfStatementParser icici() {
        return new PdfStatementParser("ICICI_STATEMENT_PASSWORD",
                "Savings\\s+A/c\\s+X{3,}(\\d{4})\\s+(-?[\\d,]+(?:\\.\\d{1,2})?)",
                "as\\s+on\\s+([A-Za-z]+\\s+\\d{1,2},\\s*\\d{4})", "MMMM d, yyyy");
    }

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final String passwordEnv;
    private final Pattern balance;
    private final Pattern date;
    private final DateTimeFormatter dateFormat;

    public PdfStatementParser(String passwordEnv, String balanceRegex, String dateRegex, String datePattern) {
        this.passwordEnv = passwordEnv;
        this.balance = Pattern.compile(balanceRegex, Pattern.CASE_INSENSITIVE);
        this.date = Pattern.compile(dateRegex, Pattern.CASE_INSENSITIVE);
        this.dateFormat = DateTimeFormatter.ofPattern(datePattern, Locale.ENGLISH);
    }

    @Override
    public boolean needsPdfs() {
        return true;
    }

    @Override
    public Optional<ParsedBalance> parse(GmailMessage message) {
        String password = System.getenv(passwordEnv);
        if (password == null || password.isBlank()) {
            log.warn("{} is not set; cannot open statement PDF", passwordEnv);
            return Optional.empty();
        }
        for (byte[] pdf : message.pdfs()) {
            Optional<ParsedBalance> p = parseText(text(pdf, password), message);
            if (p.isPresent()) return p;
        }
        return Optional.empty();
    }

    Optional<ParsedBalance> parseText(String text, GmailMessage message) {
        if (text == null) return Optional.empty();
        Matcher m = balance.matcher(text);
        if (!m.find()) return Optional.empty();
        BigDecimal amount;
        try {
            amount = new BigDecimal(m.group(2).replace(",", ""));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
        return Optional.of(new ParsedBalance(amount, asOf(text, message), m.group(1)));
    }

    private static String text(byte[] pdf, String password) {
        try (PDDocument doc = Loader.loadPDF(pdf, password)) {
            return new PDFTextStripper().getText(doc);
        } catch (IOException e) {
            log.warn("Could not read statement PDF: {}", e.getMessage());
            return null;
        }
    }

    private LocalDate asOf(String text, GmailMessage message) {
        Matcher m = date.matcher(text);
        if (m.find()) {
            try {
                return LocalDate.parse(m.group(1).replaceAll("\\s+", " "), dateFormat);
            } catch (DateTimeParseException ignored) {
                // fall back to the received date
            }
        }
        return LocalDate.ofInstant(message.receivedAt(), IST);
    }
}
