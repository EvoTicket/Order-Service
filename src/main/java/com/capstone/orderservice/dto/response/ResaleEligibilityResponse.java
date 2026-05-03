package com.capstone.orderservice.dto.response;

import com.capstone.orderservice.enums.TicketAccessStatus;
import com.capstone.orderservice.enums.TicketChainStatus;
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
public class ResaleEligibilityResponse {
    private Long ticketAssetId;
    private Boolean canResell;
    private String reasonCode;
    private String reasonMessage;
    private BigDecimal originalPrice;
    private BigDecimal priceCap;
    private BigDecimal platformFeeRate;
    private BigDecimal organizerRoyaltyRate;
    private TicketAccessStatus accessStatus;
    private TicketChainStatus chainStatus;
    private Long currentResaleListingId;
    private LocalDateTime eventEndTime;
}
