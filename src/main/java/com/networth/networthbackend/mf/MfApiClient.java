package com.networth.networthbackend.mf;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Fetches one scheme's full NAV history from mfapi.in.
 * <p>
 * Used only for the initial backfill. AMFI publishes today's NAV for every scheme in one file but
 * offers no usable bulk history endpoint, whereas mfapi.in serves inception-to-date for a scheme in
 * a single call — so a full backfill is one request per scheme rather than one per scheme-day.
 * The daily path never touches this service; it reads AMFI's NAVAll snapshot instead.
 */
@Slf4j
public class MfApiClient {

    private static final String HISTORY_URL = "https://api.mfapi.in/mf/";

    /** Some schemes return 3,000+ points; the payload is ~130 KB and the origin is not fast. */
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);

    /** mfapi.in returns {@code dd-MM-yyyy}, unlike AMFI's {@code dd-MMM-yyyy}. */
    private static final DateTimeFormatter MFAPI_DATE =
            DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.ENGLISH);

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Full NAV history for a scheme, oldest first.
     * <p>
     * Returns an empty list when the scheme has no history. That is not distinguishable from an
     * error by status code — mfapi.in answers an unknown scheme code with <b>HTTP 200</b> and an
     * empty {@code data} array rather than a 404 — so the caller must treat empty as "nothing to
     * store" and must not mark such a scheme as successfully backfilled on the strength of a 200.
     *
     * @throws IOException on a non-200 status or unparseable body, so the caller can retry
     */
    public List<AmfiClient.NavQuote> fetchHistory(int schemeCode) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(HISTORY_URL + schemeCode))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("mfapi.in returned HTTP " + response.statusCode()
                    + " for scheme " + schemeCode);
        }

        JsonNode root = mapper.readTree(response.body());
        JsonNode data = root.path("data");
        if (!data.isArray()) {
            throw new IOException("mfapi.in returned no data array for scheme " + schemeCode);
        }

        List<AmfiClient.NavQuote> out = new ArrayList<>(data.size());
        for (JsonNode point : data) {
            LocalDate date = parseDate(point.path("date").asText(null));
            BigDecimal nav = AmfiClient.parseNav(point.path("nav").asText(null));
            // A fund that did not strike a NAV that day is published as "0" or blank. Skipping it
            // keeps a spurious -100% return out of every window that spans the date.
            if (date != null && nav != null) out.add(new AmfiClient.NavQuote(date, nav));
        }

        // The API returns newest-first; every metric walks the series forwards.
        out.sort(java.util.Comparator.comparing(AmfiClient.NavQuote::date));
        return out;
    }

    private static LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return LocalDate.parse(raw.trim(), MFAPI_DATE);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
