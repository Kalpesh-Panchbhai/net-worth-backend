package com.networth.networthbackend.syncmf;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.networth.networthbackend.api.EntityHandlerBase;
import com.networth.networthbackend.common.Constants;
import com.networth.networthbackend.db.Database;
import com.networth.networthbackend.service.KiteApiClient;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.Holding;
import com.zerodhatech.models.MFHolding;
import com.zerodhatech.models.Position;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;

@Slf4j
public class SyncMfApiHandler extends EntityHandlerBase {

    /** Kite is an Indian broker: everything it returns is denominated in INR. */
    private static final String KITE_CURRENCY = "INR";

    private final KiteApiClient kiteClient = new KiteApiClient();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(5)).build();

    @Override
    public APIGatewayProxyResponseEvent handle(String action, APIGatewayProxyRequestEvent request, Context context) throws Exception {
        if (action == null) {
            return badRequest(Constants.ErrorCodes.ACTION_REQUIRED, Constants.Messages.ACTION_REQUIRED);
        }

        String mode = queryParam(request, "mode");
        if (mode == null) {
            // Infer from HTTP method + action
            mode = action.toLowerCase();
        }

        return switch (mode) {
            case "login" -> handleLogin(request);
            case "callback" -> handleCallback(request);
            case "preview" -> handlePreview(request);
            case "confirm" -> handleConfirm(request);
            case "stock-preview" -> handleStockPreview(request);
            case "stock-confirm" -> handleStockConfirm(request);
            default -> badRequest(Constants.ErrorCodes.ACTION_NOT_SUPPORTED,
                    String.format(Constants.Messages.ACTION_NOT_SUPPORTED_FMT, "sync-mf", mode));
        };
    }

    private APIGatewayProxyResponseEvent handleLogin(APIGatewayProxyRequestEvent request) {
        if (!kiteClient.isAvailable()) {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, "Kite API credentials not configured");
        }
        String loginUrl = kiteClient.getLoginUrl();
        return ok(Map.of("loginUrl", loginUrl));
    }

    private APIGatewayProxyResponseEvent handleCallback(APIGatewayProxyRequestEvent request) throws Exception {
        JsonNode json = getJsonBody(request);
        if (json == null || !json.hasNonNull("requestToken")) {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, "requestToken is required");
        }
        String requestToken = json.get("requestToken").asText();
        try {
            String accessToken = kiteClient.exchangeToken(requestToken);
            // Store access token in DB
            storeAccessToken(accessToken);
            log.info("Kite access token stored successfully");
            return ok(Map.of("status", "success"));
        } catch (KiteException e) {
            log.error("Kite token exchange failed: {}", e.message, e);
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, "Kite login failed: " + e.message);
        }
    }

    private APIGatewayProxyResponseEvent handlePreview(APIGatewayProxyRequestEvent request) throws Exception {
        String accountIdStr = queryParam(request, "accountId");
        if (accountIdStr == null || accountIdStr.isBlank()) {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, "accountId is required");
        }
        long accountId = Long.parseLong(accountIdStr);

        String accessToken = loadAccessToken();
        if (accessToken == null) {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, "Not logged in to Kite. Please login first.");
        }

        // Fetch MF holdings from Kite
        List<MFHolding> kiteMfHoldings;
        try {
            kiteMfHoldings = kiteClient.getMfHoldings(accessToken);
        } catch (Exception | KiteException e) {
            log.error("Failed to fetch Kite MF holdings", e);
            // Delete stale token so next attempt triggers re-login immediately
            deleteAccessToken();
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, "Failed to fetch Kite holdings. Token may be expired. Please login again.");
        }

        // Get DB holdings for this account
        List<Map<String, Object>> dbHoldings = getDbHoldings(accountId);

        // Build diffs
        List<Map<String, Object>> diffs = new ArrayList<>();
        for (MFHolding kh : kiteMfHoldings) {
            String yahooSymbol = resolveIsinToYahoo(kh.tradingsymbol);
            if (yahooSymbol == null) {
                log.warn("Could not resolve ISIN {} to Yahoo symbol", kh.tradingsymbol);
                yahooSymbol = kh.tradingsymbol; // fallback
            }

            BigDecimal kiteUnits = BigDecimal.valueOf(kh.quantity).setScale(3, RoundingMode.HALF_UP);
            BigDecimal kiteInvested = BigDecimal.valueOf(kh.quantity * kh.averagePrice).setScale(2, RoundingMode.HALF_UP);

            // Find matching DB holding by symbol
            Map<String, Object> dbMatch = null;
            for (Map<String, Object> dbh : dbHoldings) {
                if (yahooSymbol.equals(dbh.get("symbol"))) {
                    dbMatch = dbh;
                    break;
                }
            }

            Map<String, Object> diff = new LinkedHashMap<>();
            diff.put("fund", kh.fund);
            diff.put("isin", kh.tradingsymbol);
            diff.put("yahooSymbol", yahooSymbol);
            diff.put("kiteUnits", kiteUnits);
            diff.put("kiteInvested", kiteInvested);

            if (dbMatch != null) {
                long holdingId = (long) dbMatch.get("holdingId");
                BigDecimal dbUnits = (BigDecimal) dbMatch.get("dbUnits");
                BigDecimal dbInvested = (BigDecimal) dbMatch.get("dbInvested");
                diff.put("holdingId", holdingId);
                diff.put("holdingName", dbMatch.get("holdingName"));
                diff.put("dbUnits", dbUnits);
                diff.put("dbInvested", dbInvested);
                BigDecimal normDbUnits = dbUnits.setScale(3, RoundingMode.HALF_UP);
                BigDecimal normDbInvested = dbInvested.setScale(2, RoundingMode.HALF_UP);
                diff.put("unitsDiff", kiteUnits.subtract(normDbUnits));
                diff.put("investedDiff", kiteInvested.subtract(normDbInvested));

                boolean unitsMatch = kiteUnits.compareTo(normDbUnits) == 0;
                boolean investedMatch = kiteInvested.compareTo(normDbInvested) == 0;
                diff.put("status", unitsMatch && investedMatch ? "UNCHANGED" : "CHANGED");
            } else {
                diff.put("holdingId", null);
                diff.put("holdingName", null);
                diff.put("dbUnits", BigDecimal.ZERO);
                diff.put("dbInvested", BigDecimal.ZERO);
                diff.put("unitsDiff", kiteUnits);
                diff.put("investedDiff", kiteInvested);
                diff.put("status", "NEW");
            }
            diffs.add(diff);
        }

        // Kite reports in INR and these figures are shown for reconciliation against the stored
        // values, so they are deliberately NOT converted. Name the currency explicitly rather than
        // letting the client fall back to a default and label INR amounts with something else.
        return ok(Map.of("diffs", diffs, "accountId", accountId, "currency", KITE_CURRENCY));
    }

    private APIGatewayProxyResponseEvent handleConfirm(APIGatewayProxyRequestEvent request) throws Exception {
        JsonNode json = getJsonBody(request);
        if (json == null || !json.hasNonNull("accountId") || !json.hasNonNull("diffs")) {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, "accountId and diffs are required");
        }
        long accountId = json.get("accountId").asLong();
        LocalDate defaultDate = LocalDate.now();

        JsonNode diffsNode = json.get("diffs");
        int inserted = 0;

        try (Connection conn = Database.getConnection()) {
            for (JsonNode d : diffsNode) {
                String status = d.path("status").asText();
                if ("UNCHANGED".equals(status)) continue;

                // Per-holding transaction date, fallback to today
                LocalDate txnDate = d.hasNonNull("txnDate") && !d.path("txnDate").asText().isBlank()
                        ? LocalDate.parse(d.path("txnDate").asText())
                        : defaultDate;

                String yahooSymbol = d.path("yahooSymbol").asText();
                BigDecimal kiteUnits = d.path("kiteUnits").decimalValue();
                BigDecimal kiteInvested = d.path("kiteInvested").decimalValue();
                String fundName = d.path("fund").asText();
                long holdingId;

                if ("NEW".equals(status)) {
                    // Create holding first
                    String insertHolding = "INSERT INTO holdings (account_id, name, symbol) VALUES (?, ?, ?)";
                    try (PreparedStatement ps = conn.prepareStatement(insertHolding, Statement.RETURN_GENERATED_KEYS)) {
                        ps.setLong(1, accountId);
                        ps.setString(2, fundName);
                        ps.setString(3, yahooSymbol);
                        ps.executeUpdate();
                        try (ResultSet rs = ps.getGeneratedKeys()) {
                            rs.next();
                            holdingId = rs.getLong(1);
                        }
                    }
                    log.info("Created new holding: id={}, name={}, symbol={}", holdingId, fundName, yahooSymbol);
                } else {
                    holdingId = d.path("holdingId").asLong();
                }

                // Insert or update transaction using ON DUPLICATE KEY UPDATE (unique key on holding_id + txn_date)
                String upsertTxn = "INSERT INTO transactions (account_id, holding_id, txn_date, invested, value) VALUES (?, ?, ?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE invested = VALUES(invested), value = VALUES(value)";
                try (PreparedStatement ps = conn.prepareStatement(upsertTxn)) {
                    ps.setLong(1, accountId);
                    ps.setLong(2, holdingId);
                    ps.setDate(3, java.sql.Date.valueOf(txnDate));
                    ps.setBigDecimal(4, kiteInvested);
                    ps.setBigDecimal(5, kiteUnits);
                    ps.executeUpdate();
                }
                inserted++;
                log.info("Upserted transaction: holdingId={}, date={}, invested={}, units={}", holdingId, txnDate, kiteInvested, kiteUnits);
            }
        }

        return ok(Map.of("inserted", inserted));
    }

    private APIGatewayProxyResponseEvent handleStockPreview(APIGatewayProxyRequestEvent request) throws Exception {
        String accountIdStr = queryParam(request, "accountId");
        if (accountIdStr == null || accountIdStr.isBlank()) {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, "accountId is required");
        }
        long accountId = Long.parseLong(accountIdStr);

        String accessToken = loadAccessToken();
        if (accessToken == null) {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, "Not logged in to Kite. Please login first.");
        }

        List<Holding> kiteHoldings;
        List<Position> kitePositions;
        try {
            kiteHoldings = kiteClient.getStockHoldings(accessToken);
            kitePositions = kiteClient.getStockPositions(accessToken);
        } catch (Exception | KiteException e) {
            log.error("Failed to fetch Kite stock holdings/positions", e);
            deleteAccessToken();
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, "Failed to fetch Kite holdings. Token may be expired. Please login again.");
        }

        BigDecimal totalInvested = BigDecimal.ZERO;
        BigDecimal totalValue = BigDecimal.ZERO;
        for (Holding h : kiteHoldings) {
            totalInvested = totalInvested.add(BigDecimal.valueOf((long) h.quantity).multiply(BigDecimal.valueOf(h.averagePrice)));
            totalValue = totalValue.add(BigDecimal.valueOf((long) h.quantity).multiply(BigDecimal.valueOf(h.lastPrice)));
        }

        // Open long positions for the day: same-day delivery (CNC) buys that have not yet
        // settled into holdings, plus intraday and F&O. Valued at market like holdings.
        // Only net-long positions count; sold/short (negative) and squared-off (zero) are skipped.
        int openPositionCount = 0;
        for (Position p : kitePositions) {
            if (p.netQuantity <= 0) continue; // only net-long open positions
            double lastPrice = p.lastPrice != null ? p.lastPrice : p.averagePrice;
            totalInvested = totalInvested.add(BigDecimal.valueOf(p.netQuantity).multiply(BigDecimal.valueOf(p.averagePrice)));
            totalValue = totalValue.add(BigDecimal.valueOf(p.netQuantity).multiply(BigDecimal.valueOf(lastPrice)));
            openPositionCount++;
        }

        totalInvested = totalInvested.setScale(2, RoundingMode.HALF_UP);
        totalValue = totalValue.setScale(2, RoundingMode.HALF_UP);

        // Check if today's transaction already exists
        boolean alreadySynced = false;
        LocalDate today = LocalDate.now();
        try (Connection conn = Database.getConnection()) {
            String sql = "SELECT 1 FROM transactions t JOIN holdings h ON t.holding_id = h.id "
                    + "WHERE h.account_id = ? AND t.txn_date = ? LIMIT 1";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, accountId);
                ps.setDate(2, java.sql.Date.valueOf(today));
                try (ResultSet rs = ps.executeQuery()) {
                    alreadySynced = rs.next();
                }
            }
        }

        // Kite's own INR figures, shown as-is for reconciliation; labelled so the client does not
        // render them with the user's display-currency symbol.
        return ok(Map.of(
                "invested", totalInvested,
                "value", totalValue,
                "currency", KITE_CURRENCY,
                "holdingCount", kiteHoldings.size() + openPositionCount,
                "alreadySynced", alreadySynced,
                "date", today.toString()
        ));
    }

    private APIGatewayProxyResponseEvent handleStockConfirm(APIGatewayProxyRequestEvent request) throws Exception {
        JsonNode json = getJsonBody(request);
        if (json == null || !json.hasNonNull("accountId")) {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, "accountId is required");
        }
        long accountId = json.get("accountId").asLong();
        BigDecimal invested = json.get("invested").decimalValue();
        BigDecimal value = json.get("value").decimalValue();
        LocalDate today = LocalDate.now();

        try (Connection conn = Database.getConnection()) {
            // Get or create default holding for this account
            long holdingId;
            String findHolding = "SELECT id FROM holdings WHERE account_id = ? LIMIT 1";
            try (PreparedStatement ps = conn.prepareStatement(findHolding)) {
                ps.setLong(1, accountId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        holdingId = rs.getLong("id");
                    } else {
                        // Create default holding
                        String insertHolding = "INSERT INTO holdings (account_id, name, symbol) VALUES (?, ?, ?)";
                        try (PreparedStatement ips = conn.prepareStatement(insertHolding, Statement.RETURN_GENERATED_KEYS)) {
                            ips.setLong(1, accountId);
                            ips.setString(2, "Zerodha Kite");
                            ips.setString(3, "__ACCT__");
                            ips.executeUpdate();
                            try (ResultSet gk = ips.getGeneratedKeys()) {
                                gk.next();
                                holdingId = gk.getLong(1);
                            }
                        }
                        log.info("Created default holding for stock account: holdingId={}", holdingId);
                    }
                }
            }

            // Upsert transaction for today
            String upsertTxn = "INSERT INTO transactions (account_id, holding_id, txn_date, invested, value) VALUES (?, ?, ?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE invested = VALUES(invested), value = VALUES(value)";
            try (PreparedStatement ps = conn.prepareStatement(upsertTxn)) {
                ps.setLong(1, accountId);
                ps.setLong(2, holdingId);
                ps.setDate(3, java.sql.Date.valueOf(today));
                ps.setBigDecimal(4, invested);
                ps.setBigDecimal(5, value);
                ps.executeUpdate();
            }
            log.info("Stock sync: accountId={}, date={}, invested={}, value={}", accountId, today, invested, value);
        }

        return ok(Map.of("synced", true, "date", today.toString()));
    }

    // --- DB helpers ---

    private void storeAccessToken(String accessToken) throws SQLException {
        String apiKey = System.getenv(Constants.Env.KITE_API_KEY);
        String sql = "INSERT INTO kite_sessions (api_key, access_token) VALUES (?, ?) "
                + "ON DUPLICATE KEY UPDATE access_token = VALUES(access_token), created_at = CURRENT_TIMESTAMP";
        try (Connection conn = Database.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, apiKey);
            ps.setString(2, accessToken);
            ps.executeUpdate();
        }
    }

    private String loadAccessToken() throws SQLException {
        String apiKey = System.getenv(Constants.Env.KITE_API_KEY);
        String sql = "SELECT access_token FROM kite_sessions WHERE api_key = ? AND created_at >= CURRENT_DATE ORDER BY created_at DESC LIMIT 1";
        try (Connection conn = Database.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, apiKey);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("access_token");
            }
        }
        return null;
    }

    private void deleteAccessToken() {
        try {
            String apiKey = System.getenv(Constants.Env.KITE_API_KEY);
            String sql = "DELETE FROM kite_sessions WHERE api_key = ?";
            try (Connection conn = Database.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, apiKey);
                ps.executeUpdate();
            }
            log.info("Deleted stale Kite access token for api_key={}", apiKey);
        } catch (SQLException e) {
            log.warn("Failed to delete stale Kite access token", e);
        }
    }

    private List<Map<String, Object>> getDbHoldings(long accountId) throws SQLException {
        String sql = """
                SELECT h.id AS holding_id, h.name AS holding_name, h.symbol,
                       COALESCE(t.value, 0) AS db_units, COALESCE(t.invested, 0) AS db_invested
                FROM holdings h
                LEFT JOIN (
                    SELECT t1.holding_id, t1.value, t1.invested
                    FROM transactions t1
                    INNER JOIN (
                        SELECT holding_id, MAX(txn_date) AS max_date
                        FROM transactions GROUP BY holding_id
                    ) t2 ON t1.holding_id = t2.holding_id AND t1.txn_date = t2.max_date
                ) t ON t.holding_id = h.id
                WHERE h.account_id = ? AND h.symbol <> '__ACCT__'
                """;
        List<Map<String, Object>> result = new ArrayList<>();
        try (Connection conn = Database.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, accountId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("holdingId", rs.getLong("holding_id"));
                    row.put("holdingName", rs.getString("holding_name"));
                    row.put("symbol", rs.getString("symbol"));
                    row.put("dbUnits", rs.getBigDecimal("db_units"));
                    row.put("dbInvested", rs.getBigDecimal("db_invested"));
                    result.add(row);
                }
            }
        }
        return result;
    }

    private String resolveIsinToYahoo(String isin) {
        try {
            String encoded = URLEncoder.encode(isin, StandardCharsets.UTF_8);
            String url = "https://query1.finance.yahoo.com/v1/finance/search?q=" + encoded + "&quotesCount=5&newsCount=0";
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .header("User-Agent", "Mozilla/5.0")
                    .header("Accept", "application/json")
                    .timeout(java.time.Duration.ofSeconds(10))
                    .GET().build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) return null;
            JsonNode root = MAPPER.readTree(resp.body());
            JsonNode quotes = root.path("quotes");
            if (quotes.isArray() && !quotes.isEmpty()) {
                return quotes.get(0).path("symbol").asText();
            }
        } catch (Exception e) {
            log.warn("Failed to resolve ISIN {} to Yahoo symbol", isin, e);
        }
        return null;
    }
}
