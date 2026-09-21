package com.networth.networthbackend.mf;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fetches AMFI's two public flat files.
 */
@Slf4j
public class AmfiClient {

    /**
     * AMFI serves Windows-1252, not UTF-8 and not strict Latin-1. "Children's Fund" carries the
     * curly apostrophe as byte {@code 0x92}, which is a printable quote in Windows-1252 but an
     * unassigned C1 control character in ISO-8859-1 — decoding it as Latin-1 puts a control char
     * into the category string, and reading it as UTF-8 yields a replacement character.
     */
    private static final Charset AMFI_CHARSET = Charset.forName("windows-1252");

    /**
     * Scheme master. Unlike NAVAll this is a real CSV with the SEBI category as a column, and it
     * includes closed and wound-up schemes, so it is a superset of what reports a NAV today.
     */
    private static final String SCHEME_DATA_URL =
            "https://portal.amfiindia.com/DownloadSchemeData_Po.aspx?mf=0";

    /** Today's NAV snapshot for every scheme still reporting. Used here only to decide liveness. */
    private static final String NAV_ALL_URL = "https://portal.amfiindia.com/spages/NAVAll.txt";

    /** AMFI serves multi-MB files from a slow origin; the default timeout is not enough. */
    private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(2);

    private static final DateTimeFormatter AMFI_DATE =
            DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH);

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /** One parsed row of the scheme-master CSV, before category/plan resolution. */
    public record SchemeRow(
            String amc,
            int schemeCode,
            String schemeName,
            String schemeType,
            String schemeCategory,
            String navName,
            LocalDate launchDate,
            String isin
    ) {
    }

    /**
     * Matches one ISIN in ISO 6166 shape: two letters, nine alphanumerics, a check digit.
     * <p>
     * Needed because the CSV's last column is not a single clean ISIN. The malformed header
     * ({@code ISIN Div Payout/ ISIN GrowthISIN Div Reinvestment}, two names run together) reflects
     * the data: AMFI concatenates the payout and reinvestment ISINs with no separator for IDCW
     * share classes, giving 24 characters. The field also carries {@code -} and {@code NA}
     * placeholders, the literal {@code Redeemed}, a ticker glued to the end
     * ({@code INF179KC1IM3HDFCNIVODG}), and sometimes a space-separated pair. Taking the first
     * match covers all of them; over the Direct+Growth set no non-empty value fails to yield one.
     */
    private static final Pattern ISIN_PATTERN = Pattern.compile("[A-Z]{2}[A-Z0-9]{9}[0-9]");

    /**
     * Column order of the scheme-master CSV. Positional because the header is malformed (see
     * {@link #ISIN_PATTERN}), so it cannot be parsed by name.
     */
    private static final int COL_AMC = 0;
    private static final int COL_CODE = 1;
    private static final int COL_NAME = 2;
    private static final int COL_TYPE = 3;
    private static final int COL_CATEGORY = 4;
    private static final int COL_NAV_NAME = 5;
    private static final int COL_LAUNCH_DATE = 7;
    private static final int COL_ISIN = 9;
    private static final int MIN_COLUMNS = 9;

    /** Downloads and parses the scheme master. Rows without a numeric scheme code are skipped. */
    public List<SchemeRow> fetchSchemeMaster() throws IOException, InterruptedException {
        String body = get(SCHEME_DATA_URL);
        List<List<String>> rows = parseCsv(body);
        List<SchemeRow> out = new ArrayList<>(rows.size());

        // Skip the header row, then every row whose first field is not a scheme code: AMFI
        // occasionally emits blank separator lines and repeated headers mid-file.
        for (List<String> r : rows) {
            if (r.size() < MIN_COLUMNS) continue;
            Integer code = parseInt(r.get(COL_CODE));
            if (code == null) continue;
            out.add(new SchemeRow(
                    trim(r, COL_AMC),
                    code,
                    trim(r, COL_NAME),
                    trim(r, COL_TYPE),
                    trim(r, COL_CATEGORY),
                    trim(r, COL_NAV_NAME),
                    parseAmfiDate(trim(r, COL_LAUNCH_DATE)),
                    firstIsin(trim(r, COL_ISIN))
            ));
        }
        log.info("AMFI scheme master: {} scheme rows parsed", out.size());
        return out;
    }

    /** One scheme's NAV on one date. */
    public record NavQuote(LocalDate date, BigDecimal nav) {
    }

    /**
     * Today's NAV snapshot, keyed by scheme code.
     * <p>
     * Only the code, NAV and date are extracted. The categories in this file are section headers
     * rather than columns, and its Plan/Option columns are unusable — see {@link MfPlanOption} — so
     * the master CSV stays the source of truth for everything except the NAV itself.
     * <p>
     * Rows whose NAV is absent or non-positive are skipped: AMFI publishes {@code N.A.} for a fund
     * that did not strike a NAV that day, and a zero would poison every return computed across it.
     */
    public Map<Integer, NavQuote> fetchLiveNavs() throws IOException, InterruptedException {
        String body = get(NAV_ALL_URL);
        Map<Integer, NavQuote> live = new HashMap<>();
        int skipped = 0;

        for (String line : body.split("\r?\n")) {
            // Scheme rows are semicolon-delimited with 8 fields; everything else in the file is a
            // blank line, an AMC name, or a "Open Ended Schemes(...)" category header.
            String[] f = line.split(";");
            if (f.length < NAV_ALL_FIELDS) continue;
            Integer code = parseInt(f[NAV_ALL_COL_CODE]);
            if (code == null) continue;

            LocalDate navDate = parseAmfiDate(f[NAV_ALL_COL_DATE].trim());
            BigDecimal nav = parseNav(f[NAV_ALL_COL_NAV]);
            if (navDate == null || nav == null) {
                skipped++;
                continue;
            }
            live.put(code, new NavQuote(navDate, nav));
        }
        log.info("AMFI NAVAll: {} schemes reporting a usable NAV ({} skipped)", live.size(), skipped);
        return live;
    }

    private static final int NAV_ALL_FIELDS = 8;
    private static final int NAV_ALL_COL_CODE = 0;
    private static final int NAV_ALL_COL_NAV = 6;
    private static final int NAV_ALL_COL_DATE = 7;

    /** Parses a NAV, returning null for blanks, {@code N.A.} and any non-positive value. */
    static BigDecimal parseNav(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            BigDecimal nav = new BigDecimal(raw.trim());
            return nav.signum() > 0 ? nav : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String get(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();
        HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            throw new IOException("AMFI returned HTTP " + response.statusCode() + " for " + url);
        }
        return new String(response.body(), AMFI_CHARSET);
    }

    /**
     * Minimal RFC 4180 reader: quoted fields, escaped {@code ""}, embedded commas and newlines.
     * <p>
     * Hand-rolled because the project has no CSV dependency and scheme names legitimately contain
     * commas, so splitting on the delimiter silently shifts every later column.
     */
    static List<List<String>> parseCsv(String text) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inQuotes) {
                if (c != '"') {
                    field.append(c);
                } else if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                    field.append('"');
                    i++;
                } else {
                    inQuotes = false;
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ',') {
                row.add(field.toString());
                field.setLength(0);
            } else if (c == '\n') {
                row.add(field.toString());
                rows.add(row);
                row = new ArrayList<>();
                field.setLength(0);
            } else if (c != '\r') {
                field.append(c);
            }
        }
        if (field.length() > 0 || !row.isEmpty()) {
            row.add(field.toString());
            rows.add(row);
        }
        return rows;
    }

    private static String trim(List<String> row, int index) {
        return index < row.size() ? row.get(index).trim() : "";
    }

    /** First ISIN in the raw field, or null when it holds no ISIN (placeholder, blank, "Redeemed"). */
    static String firstIsin(String raw) {
        if (raw == null || raw.isBlank()) return null;
        Matcher m = ISIN_PATTERN.matcher(raw.toUpperCase(Locale.ROOT));
        return m.find() ? m.group() : null;
    }

    private static Integer parseInt(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;
        for (int i = 0; i < t.length(); i++) {
            if (!Character.isDigit(t.charAt(i))) return null;
        }
        try {
            return Integer.valueOf(t);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** AMFI dates are {@code dd-MMM-yyyy}, and are sometimes blank. Never throws. */
    private static LocalDate parseAmfiDate(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return LocalDate.parse(s.trim(), AMFI_DATE);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
