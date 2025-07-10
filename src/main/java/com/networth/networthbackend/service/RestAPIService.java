package com.networth.networthbackend.service;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

public class RestAPIService {

    private final HttpClient client;

    private final String accessToken = "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzUxMiJ9.eyJpc3MiOiJkaGFuIiwicGFydG5lcklkIjoiIiwiZXhwIjoxNzUwMDY2NDg0LCJ0b2tlbkNvbnN1bWVyVHlwZSI6IlNFTEYiLCJ3ZWJob29rVXJsIjoiIiwiZGhhbkNsaWVudElkIjoiMTEwNzEwMjMwNiJ9.ZSmUK467YMQFcyudBKfNrsL9TVl6gWa43iDJfo4eCsk0nNEAEMTWS53vG6UgELIPqb4VI2FcWLVabPUuR8lSmg";

    public RestAPIService() {
        client = HttpClient.newHttpClient();
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

    public void executePostAPI(String data) throws IOException, InterruptedException {
        String token = "8064290352:AAEZKbX3WiqwjeGBYSe-ltioJwrDmX3D1wk";
        String chatId = "7310096013";

        String postData = "chat_id=" + URLEncoder.encode(chatId, StandardCharsets.UTF_8) + "&text=" + URLEncoder.encode(data, StandardCharsets.UTF_8);

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create("https://api.telegram.org/bot" + token + "/sendMessage")).header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(postData)).build();

        client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
