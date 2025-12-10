package com.capstone.orderservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplyVoucherRequest {
    @NotBlank(message = "Voucher ID không được để trống")
    private Long voucherId;

    @NotBlank(message = "Order ID không được để trống")
    private Long orderId;
}