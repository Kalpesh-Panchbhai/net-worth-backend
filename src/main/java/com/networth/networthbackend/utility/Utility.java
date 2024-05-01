package com.networth.networthbackend.utility;

import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

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

    public String convertObjToString(T data, Context context) {
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
            data = new ObjectMapper().readValue(jsonBody, type);
        } catch (JsonProcessingException e) {
            context.getLogger().log("Error while converting string to obj:::" + e.getMessage());
        }
        return data;
    }

    public String convertListOfObjToString(List<T> employees, Context context) {
        String jsonBody = null;
        try {
            jsonBody = new ObjectMapper().writeValueAsString(employees);
        } catch (JsonProcessingException e) {
            context.getLogger().log("Error while converting obj to string:::" + e.getMessage());
        }
        return jsonBody;
    }
}
