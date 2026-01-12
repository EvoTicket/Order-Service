package com.capstone.orderservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.*;

import java.util.List;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplyVoucherRequest {
    @NotEmpty(message = "Danh sách voucher không được để trống")
    private List<Long> voucherIds;

    @NotBlank(message = "Order ID không được để trống")
    private Long orderId;
}