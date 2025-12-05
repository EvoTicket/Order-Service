package com.capstone.orderservice.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ErrorResponse> handleAppException(AppException ex, HttpServletRequest request) {
        ErrorCode errorCode = ex.getErrorCode();

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(OffsetDateTime.now(ZoneOffset.ofHours(7)))
                .status(errorCode.getStatus().value())
                .error(errorCode.getStatus().getReasonPhrase())
                .code(errorCode.getCode())
                .message(ex.getCustomMessage() != null ? ex.getCustomMessage() : errorCode.getDefaultMessage())
                .path(request.getRequestURI())
                .build();

        return new ResponseEntity<>(error, errorCode.getStatus());
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentialsException(HttpServletRequest request) {
        ErrorCode errorCode = ErrorCode.BAD_CREDENTIALS;

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(OffsetDateTime.now(ZoneOffset.ofHours(7)))
                .status(errorCode.getStatus().value())
                .error(errorCode.getStatus().getReasonPhrase())
                .code(errorCode.getCode())
                .message(errorCode.getDefaultMessage())
                .path(request.getRequestURI())
                .build();

        return new ResponseEntity<>(error, errorCode.getStatus());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationErrorResponse> handleValidationExceptions(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {

        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        ErrorCode errorCode = ErrorCode.VALIDATION_FAILED;

        ValidationErrorResponse errorResponse = ValidationErrorResponse.builder()
                .timestamp(OffsetDateTime.now(ZoneOffset.ofHours(7)))
                .status(errorCode.getStatus().value())
                .error(errorCode.getStatus().getReasonPhrase())
                .code(errorCode.getCode())
                .message(errorCode.getDefaultMessage())
                .path(request.getRequestURI())
                .validationErrors(errors)
                .build();

        return new ResponseEntity<>(errorResponse, errorCode.getStatus());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex, HttpServletRequest request) {
        ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERROR;

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(OffsetDateTime.now(ZoneOffset.ofHours(7)))
                .status(errorCode.getStatus().value())
                .error(errorCode.getStatus().getReasonPhrase())
                .code(errorCode.getCode())
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .build();

        return new ResponseEntity<>(error, errorCode.getStatus());
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<ErrorResponse> handleIOException(IOException ex, HttpServletRequest request) {
        ErrorCode errorCode = ErrorCode.IO_EXCEPTION;

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(OffsetDateTime.now(ZoneOffset.ofHours(7)))
                .status(errorCode.getStatus().value())
                .error(errorCode.getStatus().getReasonPhrase())
                .code(errorCode.getCode())
                .message(ex.getMessage() != null ? ex.getMessage() : errorCode.getDefaultMessage())
                .path(request.getRequestURI())
                .build();

        return new ResponseEntity<>(error, errorCode.getStatus());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSize(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        ErrorCode errorCode = ErrorCode.MAX_UPLOAD_SIZE_EXCEEDED;

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(OffsetDateTime.now(ZoneOffset.ofHours(7)))
                .status(errorCode.getStatus().value())
                .error(errorCode.getStatus().getReasonPhrase())
                .code(errorCode.getCode())
                .message(errorCode.getDefaultMessage() + " " + ex.getMessage())
                .path(request.getRequestURI())
                .build();

        return new ResponseEntity<>(error, errorCode.getStatus());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        ErrorCode errorCode = ErrorCode.METHOD_NOT_ALLOWED;

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(OffsetDateTime.now(ZoneOffset.ofHours(7)))
                .status(errorCode.getStatus().value())
                .error(errorCode.getStatus().getReasonPhrase())
                .code(errorCode.getCode())
                .message("Phương thức HTTP không được hỗ trợ cho endpoint này." + " " + ex.getMessage())
                .path(request.getRequestURI())
                .build();

        return new ResponseEntity<>(error, errorCode.getStatus());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(MissingServletRequestParameterException ex, HttpServletRequest request) {
        ErrorCode errorCode = ErrorCode.MISSING_PARAM;

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(OffsetDateTime.now(ZoneOffset.ofHours(7)))
                .status(errorCode.getStatus().value())
                .error(errorCode.getStatus().getReasonPhrase())
                .code(errorCode.getCode())
                .message("Thiếu tham số: " + ex.getParameterName() + " " + ex.getMessage())
                .path(request.getRequestURI())
                .build();

        return new ResponseEntity<>(error, errorCode.getStatus());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException ex, HttpServletRequest request) {
        ErrorCode errorCode = ErrorCode.DATA_INTEGRITY_VIOLATION;

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(OffsetDateTime.now(ZoneOffset.ofHours(7)))
                .status(errorCode.getStatus().value())
                .error(errorCode.getStatus().getReasonPhrase())
                .code(errorCode.getCode())
                .message("Dữ liệu không hợp lệ hoặc vi phạm ràng buộc trong cơ sở dữ liệu." + " " + ex.getMessage())
                .path(request.getRequestURI())
                .build();

        return new ResponseEntity<>(error, errorCode.getStatus());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleNotReadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        ErrorCode errorCode = ErrorCode.BAD_JSON;

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(OffsetDateTime.now(ZoneOffset.ofHours(7)))
                .status(errorCode.getStatus().value())
                .error(errorCode.getStatus().getReasonPhrase())
                .code(errorCode.getCode())
                .message("Dữ liệu đầu vào không hợp lệ hoặc không đọc được JSON." + " " + ex.getMessage())
                .path(request.getRequestURI())
                .build();

        return new ResponseEntity<>(error, errorCode.getStatus());
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ErrorResponse> handleMultipart(MultipartException ex, HttpServletRequest request) {
        ErrorCode errorCode = ErrorCode.FILE_UPLOAD_ERROR;

        ErrorResponse error = ErrorResponse.builder()
                .timestamp(OffsetDateTime.now(ZoneOffset.ofHours(7)))
                .status(errorCode.getStatus().value())
                .error(errorCode.getStatus().getReasonPhrase())
                .code(errorCode.getCode())
                .message("Lỗi khi upload file. Vui lòng kiểm tra định dạng hoặc dung lượng." + " " + ex.getMessage())
                .path(request.getRequestURI())
                .build();

        return new ResponseEntity<>(error, errorCode.getStatus());
    }
}