package com.familyledger.config;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ApiResult<T> {
    private int code;
    private String message;
    private T data;
    private long timestamp;

    public static <T> ApiResult<T> success(T data) {
        return new ApiResult<>(200, "success", data, System.currentTimeMillis());
    }

    public static <T> ApiResult<T> success(String message, T data) {
        return new ApiResult<>(200, message, data, System.currentTimeMillis());
    }

    public static <T> ApiResult<T> error(int code, String message) {
        return new ApiResult<>(code, message, null, System.currentTimeMillis());
    }

    public static <T> ApiResult<T> error(int code, String message, T data) {
        return new ApiResult<>(code, message, data, System.currentTimeMillis());
    }
}
