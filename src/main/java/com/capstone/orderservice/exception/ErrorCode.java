package com.capstone.orderservice.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {
    BAD_REQUEST("BAD_REQUEST", "Request sai định dạng hoặc tham số không hợp lệ.", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED("UNAUTHORIZED", "Bạn cần đăng nhập để truy cập tài nguyên này", HttpStatus.UNAUTHORIZED),
    BAD_CREDENTIALS("BAD_CREDENTIALS", "Email hoặc mật khẩu không đúng", HttpStatus.UNAUTHORIZED),
    FORBIDDEN("FORBIDDEN", "Bạn không có quyền truy cập tài nguyên này", HttpStatus.FORBIDDEN),
    RESOURCE_NOT_FOUND("RESOURCE_NOT_FOUND", "Resource not found", HttpStatus.NOT_FOUND),
    CONFLICT("CONFLICT", "Xung đột dữ liệu, đã có dữ liệu trùng", HttpStatus.CONFLICT),
    VALIDATION_FAILED("VALIDATION_FAILED", "Dữ liệu đầu vào không hợp lệ", HttpStatus.BAD_REQUEST),
    WAITING_FOR_OTP("WAITING_FOR_OTP", "Bạn cần chờ trước khi gửi lại OTP", HttpStatus.BAD_REQUEST),
    INTERNAL_SERVER_ERROR("INTERNAL_SERVER_ERROR", "Internal server error", HttpStatus.INTERNAL_SERVER_ERROR),
    IO_EXCEPTION("IO EXCEPTION", "Lỗi khi xử lý tệp hoặc dòng dữ liệu.", HttpStatus.INTERNAL_SERVER_ERROR),
    MAX_UPLOAD_SIZE_EXCEEDED( "MAX_UPLOAD_SIZE_EXCEEDED", "Kích thước tệp vượt quá giới hạn cho phép.", HttpStatus.PAYLOAD_TOO_LARGE),
    METHOD_NOT_ALLOWED( "METHOD_NOT_ALLOWED", "Phương thức HTTP không được hỗ trợ.", HttpStatus.METHOD_NOT_ALLOWED),
    MISSING_PARAM( "MISSING_PARAM", "Thiếu tham số trong request", HttpStatus.BAD_REQUEST),
    DATA_INTEGRITY_VIOLATION("DATA_INTEGRITY_VIOLATION", "Vi phạm ràng buộc dữ liệu.", HttpStatus.CONFLICT),
    FILE_UPLOAD_ERROR("FILE_UPLOAD_ERROR", "Lỗi khi upload tệp.", HttpStatus.BAD_REQUEST),
    BAD_JSON("BAD_JSON", "Dữ liệu đầu vào không hợp lệ hoặc không đọc được JSON.", HttpStatus.BAD_REQUEST),
    ;


    private final String code;
    private final String defaultMessage;
    private final HttpStatus status;

    ErrorCode(final String code, final String defaultMessage, final HttpStatus status) {
        this.code = code;
        this.defaultMessage = defaultMessage;
        this.status = status;
    }
}