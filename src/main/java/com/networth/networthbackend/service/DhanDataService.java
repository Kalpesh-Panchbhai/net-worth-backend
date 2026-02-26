package com.networth.networthbackend.service;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.networth.networthbackend.model.dhanapimodel.FundLimitAPIResponse;
import com.networth.networthbackend.model.dhanapimodel.HoldingAPIResponse;
import com.networth.networthbackend.model.dhanapimodel.PositionAPIResponse;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Dhan Data Service - Extracted from ChartService.java main branch
 * Contains the exact same calculation logic for Dhan account daily data
 */
public class DhanDataService {

    private final String dhanBaseURL = "https://api.dhan.co/v2/";
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String accessToken;

    public DhanDataService() {
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.accessToken = System.getenv("DHAN_ACCESS_TOKEN");
    }

    /**
     * Check if the Dhan API service is available (token is configured)
     */
    public boolean isAvailable() {
        return accessToken != null && !accessToken.trim().isEmpty();
    }

    /**
     * Calculate total account value using the exact same logic as ChartService.generateDhanNonBrokerAccountData()
     */
    public DhanAccountData calculateDhanAccountData() throws Exception {
        double openPositionsValue = getOpenPositionsValue();
        double openHoldingsValue = getOpenHoldingsValue();
        double fundBalance = getFundBalance();

        double totalValue = fundBalance + openPositionsValue + openHoldingsValue;

        return new DhanAccountData(totalValue, fundBalance, openHoldingsValue, openPositionsValue);
    }

    /**
     * Exact same logic as ChartService.getOpenPositionsValue()
     */
    private double getOpenPositionsValue() throws IOException, InterruptedException {
        String url = generateDhanURL("positions");
        String response = executeDhanAPI(url);
        List<PositionAPIResponse> positionAPIResponseList = convertStringToListOfObj(response, PositionAPIResponse.class);
        return positionAPIResponseList.stream().filter(data -> data.getNetQty() >= 0).mapToDouble(item -> item.getRealizedProfit() + item.getUnrealizedProfit()).sum();
    }

    /**
     * Exact same logic as ChartService.getOpenHoldingsValue()
     */
    private double getOpenHoldingsValue() throws IOException, InterruptedException {
        String url = generateDhanURL("holdings");
        String response = executeDhanAPI(url);
        List<HoldingAPIResponse> holdingAPIResponseList = convertStringToListOfObj(response, HoldingAPIResponse.class);
        return Objects.requireNonNullElse(holdingAPIResponseList, new ArrayList<HoldingAPIResponse>()).stream().mapToDouble(item -> item.getAvailableQty() * item.getLastTradedPrice()).sum();
    }

    /**
     * Exact same logic as ChartService.getFundBalance()
     */
    private double getFundBalance() throws IOException, InterruptedException {
        String url = generateDhanURL("fundlimit");
        String response = executeDhanAPI(url);
        FundLimitAPIResponse fundLimitAPIResponse = convertStringToObj(response, FundLimitAPIResponse.class);
        return fundLimitAPIResponse.getAvailabelBalance() + fundLimitAPIResponse.getUtilizedAmount();
    }

    private String generateDhanURL(String path) {
        return dhanBaseURL + path;
    }

    private String executeDhanAPI(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).header("Accept", "application/json").header("Content-Type", "application/json").header("access-token", accessToken).timeout(Duration.ofSeconds(30)).GET().build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Dhan API call failed: HTTP " + response.statusCode() + " - " + response.body());
        }

        return response.body();
    }

    private <T> T convertStringToObj(String jsonString, Class<T> clazz) throws IOException {
        return objectMapper.readValue(jsonString, clazz);
    }

    private <T> List<T> convertStringToListOfObj(String jsonString, Class<T> clazz) throws IOException {
        JavaType listType = objectMapper.getTypeFactory().constructCollectionType(List.class, clazz);
        return objectMapper.readValue(jsonString, listType);
    }

    /**
     * Data class to hold Dhan account calculation results
     */
    public record DhanAccountData(double totalValue, double fundBalance, double holdingsValue, double positionsValue) {
    }
}
