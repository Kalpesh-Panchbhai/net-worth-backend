package com.networth.networthbackend.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(boolean success, T data) {
    public static <T> ApiResponse<T> of(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .data(data)
                .build();
    }
}
