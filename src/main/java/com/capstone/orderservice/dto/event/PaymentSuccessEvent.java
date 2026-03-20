package com.capstone.orderservice.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentSuccessEvent {
    private Long orderCode;

    private BigDecimal amount;

    private String description;

    private String transactionDateTime;

    private String transactionId;
}
