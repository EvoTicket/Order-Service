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
public class TicketAssetResponse {
    private Long ticketAssetId;
    private String assetCode;
    private String ticketCode;
    private Long originalOrderId;
    private String originalOrderCode;
    private Long eventId;
    private String eventName;
    private Long showtimeId;
    private LocalDateTime eventStartTime;
    private LocalDateTime eventEndTime;
    private String venueName;
    private String venueAddress;
    private Long ticketTypeId;
    private String ticketTypeName;
    private BigDecimal originalPrice;
    private TicketAccessStatus accessStatus;
    private TicketChainStatus chainStatus;
    private String tokenId;
    private String txHash;
    private String contractAddress;
    private Long currentResaleListingId;
    private LocalDateTime usedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean qrAvailable;
    private Boolean canResell;
    private String resaleBlockedReason;
}
