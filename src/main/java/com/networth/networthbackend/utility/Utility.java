package com.networth.networthbackend.utility;

import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networth.networthbackend.Root;
import com.networth.networthbackend.model.RestResponse;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Utility<T> {
    public static Map<String, String> createHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("X-amazon-author", "Lipsa");
        headers.put("X-amazon-apiVersion", "v1");
        return headers;
    }

    public static RestResponse generateRestResponse(String result, Integer id) {
        return new RestResponse(result, id);
    }

    public static String convertLocalToUTC() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return formatter.format(LocalDateTime.now(ZoneId.of("UTC"))) + " +0000";
    }

    public String convertObjToString(T data, Context context) {
        String jsonBody = null;
        try {
            jsonBody = new ObjectMapper().writeValueAsString(data);
        } catch (JsonProcessingException e) {
            context.getLogger().log("Error while converting obj to string:::" + e.getMessage());
        }
        return jsonBody;
    }

    public String convertListOfObjToString(List<T> data, Context context) {
        String jsonBody = null;
        try {
            jsonBody = new ObjectMapper().writeValueAsString(data);
        } catch (JsonProcessingException e) {
            context.getLogger().log("Error while converting obj to string:::" + e.getMessage());
        }
        return jsonBody;
    }

    public T convertStringToObj(String jsonBody, Context context, Class<T> type) {
        T data = null;
        try {
            data = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false).readValue(jsonBody, type);
        } catch (JsonProcessingException e) {
            context.getLogger().log("Error while converting string to obj:::" + e.getMessage());
        }
        return data;
    }

    public List<Root> convertStringToListOfObj(String jsonBody, Context context) {
        List<Root> data = null;
        try {
            data = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false).readValue(jsonBody, new TypeReference<List<Root>>() {
            });
        } catch (JsonProcessingException e) {
            context.getLogger().log("Error while converting string to obj:::" + e.getMessage());
        }
        return data;
    }
}
