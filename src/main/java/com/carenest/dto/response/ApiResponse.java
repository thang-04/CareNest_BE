package com.carenest.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(int code, String message, T data) {

    public static final int SUCCESS_CODE = 0;

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(SUCCESS_CODE, message, data);
    }

    public static ApiResponse<Void> success(String message) {
        return new ApiResponse<>(SUCCESS_CODE, message, null);
    }

    public static <T> ApiResponse<T> error(int code, String message, T data) {
        return new ApiResponse<>(code, message, data);
    }
}
