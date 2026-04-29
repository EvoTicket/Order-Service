package com.capstone.orderservice.service;

import com.capstone.orderservice.dto.response.TicketProvenanceResponse;
import com.capstone.orderservice.entity.ResaleListing;
import com.capstone.orderservice.entity.TicketAsset;
import com.capstone.orderservice.entity.TicketProvenance;
import com.capstone.orderservice.enums.ProvenanceActionType;
import com.capstone.orderservice.exception.AppException;
import com.capstone.orderservice.exception.ErrorCode;
import com.capstone.orderservice.repository.TicketAssetRepository;
import com.capstone.orderservice.repository.TicketProvenanceRepository;
import com.capstone.orderservice.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TicketProvenanceService {
    private static final String PRIMARY_ISSUED_DESCRIPTION = "Ticket issued after primary order confirmation";
    private static final String RESALE_LISTED_DESCRIPTION = "Ticket listed for resale";
    private static final String RESALE_CANCELLED_DESCRIPTION = "Resale listing cancelled";

    private final TicketProvenanceRepository ticketProvenanceRepository;
    private final TicketAssetRepository ticketAssetRepository;
    private final JwtUtil jwtUtil;

    @Transactional
    public void recordPrimaryIssued(TicketAsset asset) {
        if (asset == null || asset.getId() == null || asset.getOriginalOrderCode() == null) {
            return;
        }

        boolean alreadyRecorded = ticketProvenanceRepository.existsByTicketAssetIdAndActionTypeAndOrderCode(
                asset.getId(),
                ProvenanceActionType.PRIMARY_ISSUED,
                asset.getOriginalOrderCode()
        );
        if (alreadyRecorded) {
            return;
        }

        Long recipientUserId = asset.getCurrentOwnerId() != null
                ? asset.getCurrentOwnerId()
                : asset.getOriginalBuyerId();

        TicketProvenance provenance = TicketProvenance.builder()
                .ticketAssetId(asset.getId())
                .fromUserId(null)
                .toUserId(recipientUserId)
                .actionType(ProvenanceActionType.PRIMARY_ISSUED)
                .orderCode(asset.getOriginalOrderCode())
                .resaleListingCode(null)
                .price(asset.getOriginalPrice())
                .txHash(asset.getTxHash())
                .tokenId(asset.getTokenId())
                .chainStatus(asset.getChainStatus() != null ? asset.getChainStatus().name() : null)
                .description(PRIMARY_ISSUED_DESCRIPTION)
                .build();

        ticketProvenanceRepository.save(provenance);
    }

    @Transactional
    public void recordResaleListed(TicketAsset asset, ResaleListing listing) {
        recordResaleEvent(asset, listing, ProvenanceActionType.RESALE_LISTED, RESALE_LISTED_DESCRIPTION);
    }

    @Transactional
    public void recordResaleCancelled(TicketAsset asset, ResaleListing listing) {
        recordResaleEvent(asset, listing, ProvenanceActionType.RESALE_CANCELLED, RESALE_CANCELLED_DESCRIPTION);
    }

    @Transactional(readOnly = true)
    public List<TicketProvenanceResponse> getProvenanceForMyTicket(Long ticketAssetId) {
        Long currentUserId = jwtUtil.getDataFromAuth().userId();
        ticketAssetRepository.findByIdAndCurrentOwnerId(ticketAssetId, currentUserId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Ticket not found"));

        return ticketProvenanceRepository.findByTicketAssetIdOrderByCreatedAtAsc(ticketAssetId)
                .stream()
                .map(TicketProvenanceResponse::fromEntity)
                .toList();
    }

    private void recordResaleEvent(
            TicketAsset asset,
            ResaleListing listing,
            ProvenanceActionType actionType,
            String description
    ) {
        if (asset == null || asset.getId() == null || listing == null || listing.getListingCode() == null) {
            return;
        }

        boolean alreadyRecorded = ticketProvenanceRepository.existsByTicketAssetIdAndActionTypeAndResaleListingCode(
                asset.getId(),
                actionType,
                listing.getListingCode()
        );
        if (alreadyRecorded) {
            return;
        }

        TicketProvenance provenance = TicketProvenance.builder()
                .ticketAssetId(asset.getId())
                .fromUserId(listing.getSellerId())
                .toUserId(null)
                .actionType(actionType)
                .orderCode(null)
                .resaleListingCode(listing.getListingCode())
                .price(listing.getListingPrice())
                .txHash(asset.getTxHash())
                .tokenId(asset.getTokenId())
                .chainStatus(asset.getChainStatus() != null ? asset.getChainStatus().name() : null)
                .description(description)
                .build();

        ticketProvenanceRepository.save(provenance);
    }
}
