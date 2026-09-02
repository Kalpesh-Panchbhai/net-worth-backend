package com.networth.networthbackend.tools;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networth.networthbackend.api.EntityHandlerBase;
import com.networth.networthbackend.common.ApiGatewayResponses;
import com.networth.networthbackend.common.Constants;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

@Slf4j
public class YahooSearchApiHandler extends EntityHandlerBase {

    private static final String YAHOO_SEARCH_URL = "https://query1.finance.yahoo.com/v1/finance/search";
    private static final java.time.Duration REQUEST_TIMEOUT = java.time.Duration.ofSeconds(10);
    // Timeouts matter now that this handler is shared: a hung socket would otherwise hold a
    // worker thread (or the whole Lambda invocation) indefinitely.
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(5))
            .build();

    @Override
    public APIGatewayProxyResponseEvent handle(String action, APIGatewayProxyRequestEvent request, Context context) throws Exception {
        if (!Constants.Actions.GET.equalsIgnoreCase(action)) {
            return badRequest(Constants.ErrorCodes.ACTION_NOT_SUPPORTED, "Only GET is supported for yahoo-search");
        }

        String query = queryParam(request, "q");
        if (query == null || query.isBlank()) {
            return badRequest(Constants.ErrorCodes.VALIDATION_ERROR, "Query parameter 'q' is required");
        }

        String encoded = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8);
        String url = YAHOO_SEARCH_URL + "?q=" + encoded + "&quotesCount=20&newsCount=0";

        log.debug("Yahoo Finance search: q={}", query);

        HttpRequest yahooReq = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", "Mozilla/5.0")
                .header("Accept", "application/json")
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();

        HttpResponse<String> resp = http.send(yahooReq, HttpResponse.BodyHandlers.ofString());

        if (resp.statusCode() / 100 != 2) {
            log.warn("Yahoo search failed: status={}, body={}", resp.statusCode(), resp.body());
            return ApiGatewayResponses.error(502, "YAHOO_ERROR", "Yahoo Finance search unavailable");
        }

        // Pass through the Yahoo response directly (it already contains quotes array)
        return ApiGatewayResponses.response(Constants.Http.Status.OK, resp.body());
    }
}
