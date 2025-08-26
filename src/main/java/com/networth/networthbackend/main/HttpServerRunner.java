package com.networth.networthbackend.main;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.networth.networthbackend.api.ApiRouterHandler;
import com.networth.networthbackend.common.Constants;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class HttpServerRunner {

    private final ApiRouterHandler handler = new ApiRouterHandler();

    public void start(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", new LambdaHttpHandler());
        server.setExecutor(null); // default executor
        log.info("HTTP server started on port {}", port);
        server.start();
    }

    private class LambdaHttpHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                String method = exchange.getRequestMethod();
                URI uri = exchange.getRequestURI();
                String path = uri.getPath();
                String body = readBody(exchange.getRequestBody());

                Map<String, String> queryParams = parseQuery(uri.getRawQuery());
                Map<String, String> headers = toSingleValueHeaders(exchange.getRequestHeaders());

                APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent()
                        .withHttpMethod(method)
                        .withPath(path)
                        .withBody(body)
                        .withQueryStringParameters(queryParams)
                        .withHeaders(headers);

                APIGatewayProxyResponseEvent response = handler.handleRequest(request, null);

                // Map response headers
                Headers respHeaders = exchange.getResponseHeaders();
                if (response.getHeaders() != null) {
                    for (Map.Entry<String, String> e : response.getHeaders().entrySet()) {
                        respHeaders.set(e.getKey(), e.getValue());
                    }
                }
                String respBody = response.getBody() != null ? response.getBody() : Constants.Api.EMPTY_JSON;
                byte[] bytes = respBody.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(response.getStatusCode(), bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            } catch (Exception e) {
                log.error("Error handling HTTP request", e);
                byte[] bytes = ("{\"" + Constants.Api.ERROR_FIELD + "\":\"" + e.getMessage() + "\"}").getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(Constants.Http.Status.INTERNAL_SERVER_ERROR, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            }
        }

        private String readBody(InputStream is) throws IOException {
            if (is == null) return null;
            byte[] buffer = is.readAllBytes();
            if (buffer.length == 0) return null;
            return new String(buffer, StandardCharsets.UTF_8);
        }

        private Map<String, String> parseQuery(String raw) {
            if (raw == null || raw.isBlank()) return null;
            Map<String, String> map = new HashMap<>();
            for (String pair : raw.split("&")) {
                int idx = pair.indexOf('=');
                String key = idx > 0 ? pair.substring(0, idx) : pair;
                String val = idx > 0 && pair.length() > idx + 1 ? pair.substring(idx + 1) : "";
                map.put(urlDecode(key), urlDecode(val));
            }
            return map;
        }

        private String urlDecode(String s) {
            return URLDecoder.decode(s, StandardCharsets.UTF_8);
        }

        private Map<String, String> toSingleValueHeaders(Headers headers) {
            if (headers == null) return Collections.emptyMap();
            Map<String, String> map = new HashMap<>();
            for (Map.Entry<String, List<String>> e : headers.entrySet()) {
                if (!e.getValue().isEmpty()) {
                    map.put(e.getKey(), e.getValue().get(0));
                }
            }
            return map;
        }
    }
}
