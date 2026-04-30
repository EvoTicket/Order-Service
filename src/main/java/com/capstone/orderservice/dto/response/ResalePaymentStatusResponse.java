package com.capstone.orderservice.dto.response;

import com.capstone.orderservice.enums.OrderStatus;
import com.capstone.orderservice.enums.PaymentMethod;
import com.capstone.orderservice.enums.ResaleListingStatus;
import com.capstone.orderservice.enums.ResalePaymentResultStatus;
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
public class ResalePaymentStatusResponse {
    private String orderCode;
    private String listingCode;
    private Long ticketAssetId;
    private PaymentMethod paymentMethod;
    private OrderStatus orderStatus;
    private ResaleListingStatus listingStatus;
    private ResalePaymentResultStatus resultStatus;
    private BigDecimal amount;
    private Long buyerId;
    private Long sellerId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime expiresAt;
    private Boolean canContinuePayment;
    private Boolean canCheckoutAgain;
    private Boolean canChooseAnotherMethod;
    private String redirectUrl;
    private String message;
}
