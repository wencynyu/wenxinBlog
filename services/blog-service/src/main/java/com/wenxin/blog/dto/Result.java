package com.wenxin.blog.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class Result<T> {
    private int code;
    private String message;
    private T data;

    public static <T> Result<T> success(T data) {
        return new Result<>(200, "ok", data);
    }
    public static <T> Result<T> success(String message, T data) {
        return new Result<>(200, message, data);
    }
    public static <T> Result<T> error(int code, String message) {
        return new Result<>(code, message, null);
    }
}
