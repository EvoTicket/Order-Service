package com.capstone.orderservice.exception;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationErrorResponse {
    private OffsetDateTime timestamp;
    private int status;
    private String error;
    private String code;
    private String path;
    private String message;
    private Map<String, String> validationErrors;
}