package com.capstone.orderservice.dto.request;

import com.capstone.orderservice.enums.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
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

    @Schema(example = "SEPAY")
    private PaymentMethod paymentMethod;

    @NotEmpty(message = "Booking Session ID không được để trống")
    private String bookingSessionId;

    @NotBlank
    @Pattern(regexp = "^[a-z]{2}$")
    @Builder.Default
    private String locale = "vi";

    private String voucherCode;
}