package com.capstone.orderservice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@AllArgsConstructor
@NoArgsConstructor
public class BaseResponse<T> {
    private int status;
    private String message;
    private T data;

    public static <T> BaseResponse<T> ok(String message, T data) {
        return new BaseResponse<>(200, message, data);
    }
    public static <T> BaseResponse<T> ok(T data) {
        return new BaseResponse<>(200, "Thành công", data);
    }

    public static <T> BaseResponse<T> created(String message, T data) {
        return new BaseResponse<>(201, message,  data);
    }
    public static <T> BaseResponse<T> created(T data) {
        return new BaseResponse<>(201, "Thành công",  data);
    }


    public static <T> BaseResponse<T> noContent(String message) {
        return new BaseResponse<>(204, message, null);
    }

    public static <T> BaseResponse<T> badRequest(String message) {
        return new BaseResponse<>(400, message, null);
    }
}
