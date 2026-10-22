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
 * Parser for the KFintech NPS transaction statement, a password-protected PDF. Reads the
 * Investment Details row ("15 250000.00 0.00 608.37 348887.81 98887.81"): Total Contribution is
 * stored as invested, Current Valuation as value. The PRAN gives the last 4 digits; dated on
 * "Investment Details as on 30-09-2026", else the day the email arrived (IST).
 * The PDF password is read from env {@value #ENV_PASSWORD}.
 */
@Slf4j
public class NpsStatementParser implements BankBalanceParser {

    static final String ENV_PASSWORD = "NPS_STATEMENT_PASSWORD";

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private static final String AMOUNT = "(-?[\\d,]+(?:\\.\\d{1,2})?)";
    // Contributions, Total Contribution, Total Withdrawal, Charges, Current Valuation
    private static final Pattern DETAILS = Pattern.compile(
            "Notional\\s+Gain/Loss\\s*\\(₹\\)\\s*\\d+\\s+" + AMOUNT + "\\s+" + AMOUNT + "\\s+" + AMOUNT + "\\s+" + AMOUNT,
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PRAN = Pattern.compile("PRAN\\s+\\d{8}(\\d{4})\\b");
    private static final Pattern AS_ON = Pattern.compile(
            "Investment\\s+Details\\s+as\\s+on\\s+(\\d{2}-\\d{2}-\\d{4})", Pattern.CASE_INSENSITIVE);
    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    @Override
    public boolean needsPdfs() {
        return true;
    }

    @Override
    public Optional<ParsedBalance> parse(GmailMessage message) {
        String password = System.getenv(ENV_PASSWORD);
        if (password == null || password.isBlank()) {
            log.warn("{} is not set; cannot open NPS statement", ENV_PASSWORD);
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
        Matcher d = DETAILS.matcher(text);
        Matcher p = PRAN.matcher(text);
        if (!d.find() || !p.find()) return Optional.empty();
        try {
            BigDecimal contribution = new BigDecimal(d.group(1).replace(",", ""));
            BigDecimal valuation = new BigDecimal(d.group(4).replace(",", ""));
            return Optional.of(new ParsedBalance(valuation, asOf(text, message), p.group(1), contribution));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static String text(byte[] pdf, String password) {
        try (PDDocument doc = Loader.loadPDF(pdf, password)) {
            return new PDFTextStripper().getText(doc);
        } catch (IOException e) {
            log.warn("Could not read NPS statement PDF: {}", e.getMessage());
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
