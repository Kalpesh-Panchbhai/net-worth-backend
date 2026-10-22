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
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser for SBI's monthly e-account statement, a password-protected PDF. Reads the savings row
 * of the Transaction Accounts table ("P SINGLE XXXXXXX1225 OPEN INR 8665.38 0.00 0.00 8665.38"):
 * the masked account number gives the last 4 digits, the first amount after the currency is the
 * current balance. Dated on "As on 30-09-26", else the day the email arrived (IST).
 * The PDF password is read from env {@value #ENV_PASSWORD}.
 */
@Slf4j
public class SbiStatementParser implements BankBalanceParser {

    static final String ENV_PASSWORD = "SBI_STATEMENT_PASSWORD";

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private static final Pattern ACCOUNT_ROW = Pattern.compile(
            "X{3,}(\\d{4})\\s+OPEN\\s+INR\\s+(-?[\\d,]+(?:\\.\\d{1,2})?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern AS_ON = Pattern.compile("As\\s+on\\s+(\\d{2}-\\d{2}-\\d{2})", Pattern.CASE_INSENSITIVE);
    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd-MM-yy");

    @Override
    public boolean needsPdfs() {
        return true;
    }

    @Override
    public Optional<ParsedBalance> parse(GmailMessage message) {
        String password = System.getenv(ENV_PASSWORD);
        if (password == null || password.isBlank()) {
            log.warn("{} is not set; cannot open SBI statement", ENV_PASSWORD);
            return Optional.empty();
        }
        for (byte[] pdf : message.pdfs()) {
            Optional<ParsedBalance> p = parseText(text(pdf, password), message);
            if (p.isPresent()) return p;
        }
        return Optional.empty();
    }

    static Optional<ParsedBalance> parseText(String text, GmailMessage message) {
        if (text == null) return Optional.empty();
        Matcher m = ACCOUNT_ROW.matcher(text);
        if (!m.find()) return Optional.empty();
        BigDecimal balance;
        try {
            balance = new BigDecimal(m.group(2).replace(",", ""));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
        return Optional.of(new ParsedBalance(balance, asOf(text, message), m.group(1)));
    }

    private static String text(byte[] pdf, String password) {
        try (PDDocument doc = Loader.loadPDF(pdf, password)) {
            return new PDFTextStripper().getText(doc);
        } catch (IOException e) {
            log.warn("Could not read SBI statement PDF: {}", e.getMessage());
            return null;
        }
    }

    private static LocalDate asOf(String text, GmailMessage message) {
        Matcher m = AS_ON.matcher(text);
        if (m.find()) {
            try {
                return LocalDate.parse(m.group(1), DMY);
            } catch (DateTimeParseException ignored) {
                // fall back to the received date
            }
        }
        return LocalDate.ofInstant(message.receivedAt(), IST);
    }
}
