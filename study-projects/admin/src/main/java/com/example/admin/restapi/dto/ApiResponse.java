package com.example.admin.restapi.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor(staticName = "of")
public class ApiResponse {
    private boolean success;
    private String message;
}