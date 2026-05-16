package com.capstone.orderservice.service;

import com.capstone.orderservice.dto.response.RichTicketProvenanceResponse;
import com.capstone.orderservice.entity.Order;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.util.*;
import java.util.stream.Collectors;
import java.time.format.DateTimeFormatter;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TicketProvenanceService {
    private static final String BLOCKCHAIN_API_BASE = "http://web3-worker-service:4500";
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy, HH:mm");
    private static final String PRIMARY_ISSUED_DESCRIPTION = "Ticket issued after primary order confirmation";
    private static final String RESALE_LISTED_DESCRIPTION = "Ticket listed for resale";
    private static final String RESALE_CANCELLED_DESCRIPTION = "Resale listing cancelled";
    private static final String RESALE_PURCHASED_DESCRIPTION = "Resale purchase completed";
    private static final String OWNERSHIP_TRANSFERRED_DESCRIPTION = "Ticket ownership transferred after resale payment";

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
                asset.getOriginalOrderCode());
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
                .fromBlock(asset.getFromBlock())
                .toBlock(asset.getToBlock())
                .contractAddress(asset.getContractAddress())
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

    @Transactional
    public void recordResalePurchased(TicketAsset asset, ResaleListing listing, Order order) {
        recordPaidResaleEvent(
                asset,
                listing,
                order,
                listing != null ? listing.getSellerId() : null,
                listing != null ? listing.getBuyerId() : null,
                ProvenanceActionType.RESALE_PURCHASED,
                RESALE_PURCHASED_DESCRIPTION);
    }

    @Transactional
    public void recordOwnershipTransferred(
            TicketAsset asset,
            ResaleListing listing,
            Order order,
            Long sellerId,
            Long buyerId) {
        recordPaidResaleEvent(
                asset,
                listing,
                order,
                sellerId,
                buyerId,
                ProvenanceActionType.OWNERSHIP_TRANSFERRED,
                OWNERSHIP_TRANSFERRED_DESCRIPTION);
    }

    @Transactional
    public void recordQrRotated(
            TicketAsset asset,
            ResaleListing listing,
            Order order,
            Long sellerId,
            Long buyerId,
            Integer oldVersion,
            Integer newVersion) {
        String description = "Ticket QR rotated after resale payment from version "
                + oldVersion + " to " + newVersion;
        recordPaidResaleEvent(
                asset,
                listing,
                order,
                sellerId,
                buyerId,
                ProvenanceActionType.QR_ROTATED,
                description);
    }

    @Transactional
    public void recordTicketUsed(TicketAsset asset, Long checkerId, String usedAtGateId) {
        if (asset == null || asset.getId() == null) {
            return;
        }

        boolean alreadyRecorded = ticketProvenanceRepository.existsByTicketAssetIdAndActionType(
                asset.getId(),
                ProvenanceActionType.CHECKED_IN);
        if (alreadyRecorded) {
            return;
        }

        String description = "Ticket checked in by checker " + (checkerId != null ? checkerId : "unknown");
        if (usedAtGateId == null) {
            usedAtGateId = " at unknown gate";
        } else {
            usedAtGateId = " at gate " + usedAtGateId;
        }

        TicketProvenance provenance = TicketProvenance.builder()
                .ticketAssetId(asset.getId())
                .fromUserId(asset.getCurrentOwnerId())
                .toUserId(null)
                .actionType(ProvenanceActionType.CHECKED_IN)
                .txHash(asset.getTxHash())
                .tokenId(asset.getTokenId())
                .contractAddress(asset.getContractAddress())
                .chainStatus(asset.getChainStatus() != null ? asset.getChainStatus().name() : null)
                .description(description + usedAtGateId)
                .build();

        ticketProvenanceRepository.save(provenance);
    }

    @Transactional(readOnly = true)
    public RichTicketProvenanceResponse getProvenanceForMyTicket(Long ticketAssetId) {
        Long currentUserId = jwtUtil.getDataFromAuth().userId();
        TicketAsset asset = ticketAssetRepository.findByIdAndCurrentOwnerId(ticketAssetId, currentUserId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Ticket not found"));

        if (asset.getTokenId() == null) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Ticket not minted on blockchain yet");
        }

        try {
            RestClient restClient = RestClient.create(BLOCKCHAIN_API_BASE);
            RichTicketProvenanceResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/blockchain/tickets/{tokenId}/history")
                            .queryParam("fromBlock", asset.getFromBlock())
                            .queryParam("toBlock", asset.getToBlock())
                            .build(asset.getTokenId()))
                    .retrieve()
                    .body(RichTicketProvenanceResponse.class);

            if (response != null) {
                RichTicketProvenanceResponse.TicketInfo ticketInfo = RichTicketProvenanceResponse.TicketInfo.builder()
                        .eventName(asset.getEventName())
                        .eventDate(asset.getEventStartTime() != null
                                ? asset.getEventStartTime().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                                : "")
                        .eventTime(asset.getEventStartTime() != null
                                ? asset.getEventStartTime().format(DateTimeFormatter.ofPattern("HH:mm"))
                                : "")
                        .venue(asset.getVenueName())
                        .ticketType(asset.getTicketTypeName())
                        .seat(asset.getTicketTypeName())
                        .ticketCode(asset.getTicketCode())
                        .tokenId(asset.getTokenId() != null ? asset.getTokenId() : null)
                        .status(asset.getAccessStatus() != null ? asset.getAccessStatus().name() : "Unknown")
                        .originalPrice(asset.getOriginalPrice() != null
                                ? String.format("%,.0fđ", asset.getOriginalPrice().doubleValue()).replace(',', '.')
                                : "0đ")
                        .mintStatus(asset.getChainStatus() != null ? asset.getChainStatus().name() : "N/A")
                        .checkInStatus(asset.getAccessStatus())
                        .build();
                response.setTicketInfo(ticketInfo);
            }
            return response;
        } catch (Exception e) {
            log.error("Failed to fetch blockchain history for tokenId: {}", asset.getTokenId(), e);
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "Failed to fetch blockchain provenance");
        }
    }


    private void recordResaleEvent(
            TicketAsset asset,
            ResaleListing listing,
            ProvenanceActionType actionType,
            String description) {
        if (asset == null || asset.getId() == null || listing == null || listing.getListingCode() == null) {
            return;
        }

        boolean alreadyRecorded = ticketProvenanceRepository
                .existsByTicketAssetIdAndActionTypeAndResaleListingCode(
                        asset.getId(),
                        actionType,
                        listing.getListingCode());
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
                .fromBlock(asset.getFromBlock())
                .toBlock(asset.getToBlock())
                .contractAddress(asset.getContractAddress())
                .chainStatus(asset.getChainStatus() != null ? asset.getChainStatus().name() : null)
                .description(description)
                .build();

        ticketProvenanceRepository.save(provenance);
    }

    private void recordPaidResaleEvent(
            TicketAsset asset,
            ResaleListing listing,
            Order order,
            Long sellerId,
            Long buyerId,
            ProvenanceActionType actionType,
            String description) {
        if (asset == null
                || asset.getId() == null
                || listing == null
                || listing.getListingCode() == null
                || order == null
                || order.getOrderCode() == null) {
            return;
        }

        boolean alreadyRecorded = ticketProvenanceRepository
                .existsByTicketAssetIdAndActionTypeAndOrderCodeAndResaleListingCode(
                        asset.getId(),
                        actionType,
                        order.getOrderCode(),
                        listing.getListingCode());
        if (alreadyRecorded) {
            return;
        }

        TicketProvenance provenance = TicketProvenance.builder()
                .ticketAssetId(asset.getId())
                .fromUserId(sellerId)
                .toUserId(buyerId)
                .actionType(actionType)
                .orderCode(order.getOrderCode())
                .resaleListingCode(listing.getListingCode())
                .price(listing.getListingPrice())
                .txHash(asset.getTxHash())
                .tokenId(asset.getTokenId())
                .fromBlock(asset.getFromBlock())
                .toBlock(asset.getToBlock())
                .contractAddress(asset.getContractAddress())
                .chainStatus(asset.getChainStatus() != null ? asset.getChainStatus().name() : null)
                .description(description)
                .build();

        ticketProvenanceRepository.save(provenance);
    }
}
