package com.capstone.orderservice.dto.response;

import com.capstone.orderservice.enums.OrderStatus;
import com.capstone.orderservice.enums.OrderType;
import com.capstone.orderservice.enums.PaymentMethod;
import com.capstone.orderservice.enums.ResaleListingStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResaleCheckoutResponse {
    private String listingCode;
    private Long listingId;
    private Long ticketAssetId;
    private Long orderId;
    private String orderCode;
    private OrderType orderType;
    private OrderStatus orderStatus;
    private PaymentMethod paymentMethod;
    private BigDecimal amount;
    private BigDecimal listingPrice;
    private BigDecimal platformFeeAmount;
    private BigDecimal sellerPayoutAmount;
    private ResaleListingStatus status;
    private String redirectUrl;
    private String message;
}
