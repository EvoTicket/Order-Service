package com.capstone.orderservice.dto.request;

import com.capstone.orderservice.enums.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderRequest {
    private String fullName;

    private String phoneNumber;

    @Schema(example = "phong.nguyencsk22@hcmut.edu.vn")
    private String email;

    @Schema(example = "PAYOS")
    private PaymentMethod paymentMethod;

    @NotEmpty(message = "Booking Session ID không được để trống")
    private String bookingSessionId;

    private List<Long> voucherIds;
}