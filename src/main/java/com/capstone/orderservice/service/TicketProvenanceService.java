package com.capstone.orderservice.service;

import com.capstone.orderservice.dto.response.RichTicketProvenanceResponse;
import com.capstone.orderservice.dto.response.BlockchainProvenanceDto;
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

        @Transactional(readOnly = true)
        public RichTicketProvenanceResponse getProvenanceForMyTicket(Long ticketAssetId) {
                Long currentUserId = jwtUtil.getDataFromAuth().userId();
                TicketAsset asset = ticketAssetRepository.findByIdAndCurrentOwnerId(ticketAssetId, currentUserId)
                                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Ticket not found"));

                // 1. Build Ticket Info
                RichTicketProvenanceResponse.TicketInfo ticketInfo = RichTicketProvenanceResponse.TicketInfo.builder()
                                .eventName(asset.getEventName())
                                .eventDate(asset.getEventStartTime() != null
                                                ? asset.getEventStartTime()
                                                                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                                                : "")
                                .eventTime(asset.getEventStartTime() != null
                                                ? asset.getEventStartTime().format(DateTimeFormatter.ofPattern("HH:mm"))
                                                : "")
                                .venue(asset.getVenueName())
                                .ticketType(asset.getTicketTypeName())
                                .seat(asset.getTicketTypeName()) // Using ticket type name as seat placeholder if seat
                                                                 // info not separate
                                .ticketCode(asset.getTicketCode())
                                .tokenId(asset.getTokenId() != null ? "#" + asset.getTokenId() : null)
                                .status(asset.getAccessStatus() != null ? asset.getAccessStatus().name() : "Unknown")
                                .originalPrice(asset.getOriginalPrice() != null
                                                ? String.format("%,.0fđ", asset.getOriginalPrice().doubleValue())
                                                                .replace(',', '.')
                                                : "0đ")
                                .mintStatus(asset.getChainStatus() != null ? asset.getChainStatus().name() : "N/A")
                                .checkInStatus(asset.getUsedAt() != null ? "used" : "unused")
                                .build();

                // 2. Fetch Blockchain Data
                BlockchainProvenanceDto blockchainDto = null;
                if (asset.getTokenId() != null) {
                        try {
                                RestClient restClient = RestClient.create(BLOCKCHAIN_API_BASE);
                                blockchainDto = restClient.get()
                                                .uri(uriBuilder -> uriBuilder
                                                                .path("/api/blockchain/tickets/{tokenId}/provenance")
                                                                .queryParam("fromBlock", asset.getFromBlock())
                                                                .queryParam("toBlock", asset.getToBlock())
                                                                .build(asset.getTokenId()))
                                                .retrieve()
                                                .body(BlockchainProvenanceDto.class);
                        } catch (Exception e) {
                                log.warn("Failed to fetch blockchain provenance for tokenId: {}", asset.getTokenId(),
                                                e);
                        }
                }

                // 3. Build Blockchain Info
                RichTicketProvenanceResponse.BlockchainInfo blockchainInfo = RichTicketProvenanceResponse.BlockchainInfo
                                .builder()
                                .network("Polygon")
                                .onChainStatus(asset.getTokenId() != null ? "recorded" : "pending")
                                .transactionHash(asset.getTxHash())
                                .contractAddress(asset.getContractAddress())
                                .fromBlock(asset.getFromBlock())
                                .toBlock(asset.getToBlock())
                                .lastUpdated(asset.getUpdatedAt() != null
                                                ? asset.getUpdatedAt().format(DATE_TIME_FORMATTER)
                                                : null)
                                .build();

                // 4. Merge History
                List<RichTicketProvenanceResponse.HistoryEntry> history = new ArrayList<>();

                // Add local history
                List<TicketProvenance> localHistory = ticketProvenanceRepository
                                .findByTicketAssetIdOrderByCreatedAtAsc(ticketAssetId);
                for (TicketProvenance prov : localHistory) {
                        history.add(mapLocalToHistoryEntry(prov));
                }

                // Add blockchain history if available and not already in local history
                if (blockchainDto != null && blockchainDto.getHistory() != null) {
                        Set<String> existingTxHashes = localHistory.stream()
                                        .map(TicketProvenance::getTxHash)
                                        .filter(Objects::nonNull)
                                        .collect(Collectors.toSet());

                        for (BlockchainProvenanceDto.BlockchainHistoryEntry bcEntry : blockchainDto.getHistory()) {
                                if (!existingTxHashes.contains(bcEntry.getTransactionHash())) {
                                        history.add(mapBlockchainToHistoryEntry(bcEntry));
                                }
                        }
                }

                // Sort by timestamp
                // history.sort(Comparator.comparing(RichTicketProvenanceResponse.HistoryEntry::getTimestamp).reversed());

                return RichTicketProvenanceResponse.builder()
                                .ticket(ticketInfo)
                                .blockchain(blockchainInfo)
                                .history(history)
                                .build();
        }

        private RichTicketProvenanceResponse.HistoryEntry mapLocalToHistoryEntry(TicketProvenance prov) {
                Map<String, Object> details = new HashMap<>();
                String type = prov.getActionType() != null ? prov.getActionType().name() : "UNKNOWN";
                String title = type.toLowerCase();

                switch (prov.getActionType()) {
                        case PRIMARY_ISSUED -> {
                                type = "ISSUED";
                                details.put("tokenId", prov.getTokenId() != null ? "#" + prov.getTokenId() : null);
                                details.put("txHash", shortenHash(prov.getTxHash()));
                        }
                        case OWNERSHIP_TRANSFERRED -> {
                                type = "OWNERSHIP_ASSIGNED";
                                details.put("fromWallet", shortenHash(prov.getTxHash())); // Placeholder
                                details.put("toWallet", shortenHash(prov.getTxHash())); // Placeholder
                                details.put("txHash", shortenHash(prov.getTxHash()));
                        }
                        case RESALE_LISTED -> {
                                type = "RESOLD";
                                details.put("status", "locked");
                        }
                        case RESALE_PURCHASED -> {
                                type = "RESOLD";
                                details.put("status", "sold");
                        }
                        // Add other cases as needed
                }

                return RichTicketProvenanceResponse.HistoryEntry.builder()
                                .type(type)
                                .title("event_" + title)
                                .description("event_" + title + "_desc")
                                .timestamp(prov.getCreatedAt() != null ? prov.getCreatedAt().format(DATE_TIME_FORMATTER)
                                                : "")
                                .details(details)
                                .build();
        }

        private RichTicketProvenanceResponse.HistoryEntry mapBlockchainToHistoryEntry(
                        BlockchainProvenanceDto.BlockchainHistoryEntry bcEntry) {
                Map<String, Object> details = new HashMap<>();
                details.put("fromWallet", shortenHash(bcEntry.getFrom()));
                details.put("toWallet", shortenHash(bcEntry.getTo()));
                details.put("txHash", shortenHash(bcEntry.getTransactionHash()));
                details.put("blockNumber", bcEntry.getBlockNumber());

                return RichTicketProvenanceResponse.HistoryEntry.builder()
                                .type("TRANSFERRED")
                                .title("event_transferred")
                                .description("event_transferred_desc")
                                .timestamp(bcEntry.getTimestamp()) // Should ideally parse and format
                                .details(details)
                                .build();
        }

        private String shortenHash(String hash) {
                if (hash == null || hash.length() < 10)
                        return hash;
                return hash.substring(0, 6) + "..." + hash.substring(hash.length() - 4);
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
