package com.capstone.orderservice.client;

import com.capstone.orderservice.enums.PaymentMethod;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentTransactionResponse {
    private Long id;
    private String orderCode;
    private String transactionId;
    private PaymentMethod paymentMethod;
    private PaymentStatus status;
    private BigDecimal amount;
    private String buyerName;
    private String buyerEmail;
    private String description;
    private String transactionDateTime;
    private String paymentLinkUrl;
    private EventPublishStatus eventPublishStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}