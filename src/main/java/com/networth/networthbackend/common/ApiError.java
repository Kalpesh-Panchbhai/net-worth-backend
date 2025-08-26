package com.networth.networthbackend.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiError {
    String code;
    String message;

    public static ApiError of(String code, String message) {
        return ApiError.builder().code(code).message(message).build();
    }
}
