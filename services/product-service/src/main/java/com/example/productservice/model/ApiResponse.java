package com.example.productservice.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiResponse<T> {
    private int status;
    private String message;
    private T data;
    private String error;
    private String path;
    private Instant timestamp;

    // convenience factory methods
    public static <T> ApiResponse<T> success(String message, T data,String path) {
        return ApiResponse.<T>builder()
                .timestamp(Instant.now())
                .status(HttpStatus.OK.value())
                .message(message)
                .data(data)
                .path(path)
                .build();
    }

    public static <T> ApiResponse<T> error(String message, HttpStatus status, String path) {
        return ApiResponse.<T>builder()
                .timestamp(Instant.now())
                .status(status.value())
                .message(message)
                .error(status.getReasonPhrase())
                .path(path)
                .build();
    }
}