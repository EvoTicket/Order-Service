package com.capstone.orderservice.dto.request;

import com.capstone.orderservice.enums.VoucherStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateVoucherRequest {
    private BigDecimal minOrderAmount;
    private BigDecimal discountValue;
    private BigDecimal maxDiscount;
    private Integer quantityTotal;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private VoucherStatus voucherStatus;
}