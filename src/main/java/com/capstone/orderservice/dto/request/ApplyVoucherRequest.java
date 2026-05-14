package com.capstone.orderservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplyVoucherRequest {
    @NotBlank(message = "Session ID không được để trống")
    private String sessionId;
    
    @NotBlank(message = "Voucher code không được để trống")
    private String voucherCode;
}