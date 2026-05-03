package com.capstone.orderservice.dto.response;

import com.capstone.orderservice.entity.TicketProvenance;
import com.capstone.orderservice.enums.ProvenanceActionType;
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
public class TicketProvenanceResponse {
    private Long id;
    private Long ticketAssetId;
    private Long fromUserId;
    private Long toUserId;
    private ProvenanceActionType actionType;
    private String orderCode;
    private String resaleListingCode;
    private BigDecimal price;
    private String txHash;
    private String tokenId;
    private String chainStatus;
    private String description;
    private LocalDateTime createdAt;

    public static TicketProvenanceResponse fromEntity(TicketProvenance provenance) {
        return TicketProvenanceResponse.builder()
                .id(provenance.getId())
                .ticketAssetId(provenance.getTicketAssetId())
                .fromUserId(provenance.getFromUserId())
                .toUserId(provenance.getToUserId())
                .actionType(provenance.getActionType())
                .orderCode(provenance.getOrderCode())
                .resaleListingCode(provenance.getResaleListingCode())
                .price(provenance.getPrice())
                .txHash(provenance.getTxHash())
                .tokenId(provenance.getTokenId())
                .chainStatus(provenance.getChainStatus())
                .description(provenance.getDescription())
                .createdAt(provenance.getCreatedAt())
                .build();
    }
}
