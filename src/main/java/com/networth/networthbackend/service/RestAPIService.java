package com.networth.networthbackend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.networth.networthbackend.model.dhanapimodel.LedgerAPIResponse;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class RestAPIService {

    private final HttpClient client;

    private final String accessToken;
    private final String telegramToken;
    private final String telegramChatId;

    public RestAPIService() {
        client = HttpClient.newHttpClient();
        accessToken = System.getenv("ACCESS_TOKEN");
        telegramToken = System.getenv("TELEGRAM_TOKEN");
        telegramChatId = System.getenv("TELEGRAM_CHAT_ID");
    }

    public String executeAPI(String url) throws IOException, URISyntaxException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder().uri(new URL(url).toURI()).header("User-Agent", "MyYahooClient/1.0 Net-Worth API").header("Accept", "application/json").GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return response.body();
    }

    public String executeDhanAPI(String url) throws IOException, URISyntaxException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder().uri(new URL(url).toURI()).header("access-token", accessToken).GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return response.body();
    }

    public List<LedgerAPIResponse> executeDhanLedgerAPI(String url) throws IOException, URISyntaxException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder().uri(new URL(url).toURI()).header("access-token", accessToken).GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper.readValue(response.body(), new TypeReference<>() {
        });
    }

    public void executePostAPI(String data) throws IOException, InterruptedException {
        String postData = "chat_id=" + URLEncoder.encode(telegramChatId, StandardCharsets.UTF_8) + "&text=" + URLEncoder.encode(data, StandardCharsets.UTF_8) + "&parse_mode=MarkdownV2";

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create("https://api.telegram.org/bot" + telegramToken + "/sendMessage")).header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(postData)).build();

        client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
