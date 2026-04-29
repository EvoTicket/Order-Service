package com.capstone.orderservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResaleQuoteResponse {
    private Long ticketAssetId;
    private BigDecimal originalPrice;
    private BigDecimal listingPrice;
    private BigDecimal priceCap;
    private BigDecimal platformFeeRate;
    private BigDecimal organizerRoyaltyRate;
    private BigDecimal platformFeeAmount;
    private BigDecimal organizerRoyaltyAmount;
    private BigDecimal sellerPayoutAmount;
    private Boolean valid;
    private String reasonCode;
    private String reasonMessage;
}
