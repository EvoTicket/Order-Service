package com.capstone.orderservice.service;

import com.capstone.orderservice.client.EventDetailInternalResponse;
import com.capstone.orderservice.client.InventoryFeignClient;
import com.capstone.orderservice.dto.response.MyTicketGroupResponse;
import com.capstone.orderservice.dto.response.MyTicketItemResponse;
import com.capstone.orderservice.dto.response.ResaleEligibilityResponse;
import com.capstone.orderservice.dto.response.TicketAssetResponse;
import com.capstone.orderservice.dto.request.Web3MintWebhookRequest;
import com.capstone.orderservice.entity.Order;
import com.capstone.orderservice.entity.OrderItem;
import com.capstone.orderservice.entity.TicketAsset;
import com.capstone.orderservice.enums.OrderStatus;
import com.capstone.orderservice.enums.OrderType;
import com.capstone.orderservice.enums.TicketAccessStatus;
import com.capstone.orderservice.enums.TicketChainStatus;
import com.capstone.orderservice.exception.AppException;
import com.capstone.orderservice.exception.ErrorCode;
import com.capstone.orderservice.repository.TicketAssetRepository;
import com.capstone.orderservice.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TicketAssetService {
    private static final BigDecimal PRICE_CAP_MULTIPLIER = new BigDecimal("1.10");
    private static final BigDecimal PLATFORM_FEE_RATE = new BigDecimal("0.02");
    private static final BigDecimal ORGANIZER_ROYALTY_RATE = BigDecimal.ZERO;

    private final TicketAssetRepository ticketAssetRepository;
    private final InventoryFeignClient inventoryFeignClient;
    private final JwtUtil jwtUtil;
    private final TicketProvenanceService ticketProvenanceService;

    @Transactional
    public void issueTicketsForConfirmedOrder(Order order) {
        OrderType orderType = order.getOrderType() != null ? order.getOrderType() : OrderType.PRIMARY;
        if (orderType != OrderType.PRIMARY || order.getOrderStatus() != OrderStatus.CONFIRMED) {
            return;
        }

        Map<Long, Optional<EventDetailInternalResponse>> metadataByTicketTypeId = new HashMap<>();

        for (OrderItem item : order.getOrderItems()) {
            if (ticketAssetRepository.existsByOrderItem_Id(item.getId())) {
                continue;
            }

            Optional<EventDetailInternalResponse> eventMetadata = metadataByTicketTypeId.computeIfAbsent(
                    item.getTicketTypeId(),
                    this::fetchEventMetadata
            );

            TicketAsset asset = buildTicketAsset(order, item, eventMetadata.orElse(null));
            TicketAsset savedAsset = ticketAssetRepository.save(asset);
            ticketProvenanceService.recordPrimaryIssued(savedAsset);
        }
    }

    @Transactional(readOnly = true)
    public List<MyTicketGroupResponse> getMyTickets() {
        Long currentUserId = jwtUtil.getDataFromAuth().userId();
        List<TicketAsset> assets = ticketAssetRepository.findByCurrentOwnerId(currentUserId);

        Map<Long, List<TicketAsset>> assetsByOrder = assets.stream()
                .collect(Collectors.groupingBy(TicketAsset::getOriginalOrderId));

        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern(
                "HH:mm '·' EEEE, dd/MM/yyyy",
                Locale.of("vi", "VN")
        );

        return assetsByOrder.entrySet().stream().map(entry -> {
            Long orderId = entry.getKey();
            List<TicketAsset> groupAssets = entry.getValue();
            TicketAsset firstAsset = groupAssets.getFirst();

            String eventName = firstAsset.getEventName();
            String dateStr = firstAsset.getEventStartTime() != null 
                ? firstAsset.getEventStartTime().format(dateFormatter) 
                : "";
            
            String venue = firstAsset.getVenueName();
            if (firstAsset.getVenueAddress() != null && !firstAsset.getVenueAddress().isEmpty()) {
                venue += ", " + firstAsset.getVenueAddress();
            }

            Map<String, Long> countByType = groupAssets.stream()
                .collect(Collectors.groupingBy(TicketAsset::getTicketTypeName, Collectors.counting()));
            String summary = countByType.entrySet().stream()
                .map(e -> e.getValue() + " " + e.getKey())
                .collect(Collectors.joining(" · "));

            Map<String, Long> countByStatus = groupAssets.stream()
                .collect(Collectors.groupingBy(this::getDisplayStatusName, Collectors.counting()));
            String statusSummary = countByStatus.entrySet().stream()
                .map(e -> e.getValue() + " " + e.getKey())
                .collect(Collectors.joining(" · "));

            List<MyTicketItemResponse> tickets = groupAssets.stream().map(asset -> {
                String status = determineTicketStatus(asset);
                String tokenIdStr = asset.getTokenId();
                if (tokenIdStr != null && !tokenIdStr.startsWith("#")) {
                    tokenIdStr = "#" + tokenIdStr;
                }
                return MyTicketItemResponse.builder()
                        .id(asset.getId())
                        .ticketName(asset.getTicketTypeName())
                        .ticketType(asset.getTicketTypeName())
                        .seat(asset.getTicketTypeName())
                        .ticketCode(asset.getTicketCode())
                        .tokenId(tokenIdStr != null ? tokenIdStr : "")
                        .status(status)
                        .build();
            }).toList();

            return MyTicketGroupResponse.builder()
                    .orderId(orderId)
                    .eventName(eventName)
                    .date(dateStr)
                    .venue(venue)
                    .orderCode(firstAsset.getOriginalOrderCode())
                    .totalTickets(groupAssets.size())
                    .summary(summary)
                    .statusSummary(statusSummary)
                    .tickets(tickets)
                    .build();
        }).toList();
    }

    @Transactional(readOnly = true)
    public TicketAssetResponse getMyTicketDetail(Long ticketAssetId) {
        Long currentUserId = jwtUtil.getDataFromAuth().userId();
        TicketAsset asset = ticketAssetRepository.findByIdAndCurrentOwnerId(ticketAssetId, currentUserId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Ticket not found"));

        return toTicketAssetResponse(asset, currentUserId, LocalDateTime.now());
    }

    @Transactional(readOnly = true)
    public ResaleEligibilityResponse getResaleEligibility(Long ticketAssetId) {
        Long currentUserId = jwtUtil.getDataFromAuth().userId();
        TicketAsset asset = ticketAssetRepository.findById(ticketAssetId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Ticket not found"));

        EligibilityDecision decision = evaluateResaleEligibility(asset, currentUserId, LocalDateTime.now());
        BigDecimal originalPrice = asset.getOriginalPrice() != null ? asset.getOriginalPrice() : BigDecimal.ZERO;

        return ResaleEligibilityResponse.builder()
                .ticketAssetId(asset.getId())
                .canResell(decision.canResell())
                .reasonCode(decision.reasonCode())
                .reasonMessage(decision.reasonMessage())
                .originalPrice(asset.getOriginalPrice())
                .priceCap(originalPrice.multiply(PRICE_CAP_MULTIPLIER))
                .platformFeeRate(PLATFORM_FEE_RATE)
                .organizerRoyaltyRate(ORGANIZER_ROYALTY_RATE)
                .accessStatus(asset.getAccessStatus())
                .chainStatus(asset.getChainStatus())
                .currentResaleListingId(asset.getCurrentResaleListingId())
                .eventEndTime(asset.getEventEndTime())
                .build();
    }

    private Optional<EventDetailInternalResponse> fetchEventMetadata(Long ticketTypeId) {
        try {
            return Optional.ofNullable(inventoryFeignClient.getEventDetailsByTicketTypeId(ticketTypeId).getData());
        } catch (Exception e) {
            log.warn("Failed to fetch event metadata for ticketTypeId={}. Issuing TicketAsset without display metadata.",
                    ticketTypeId, e);
            return Optional.empty();
        }
    }

    private TicketAsset buildTicketAsset(
            Order order,
            OrderItem item,
            EventDetailInternalResponse eventMetadata
    ) {
        EventDetailInternalResponse.ShowtimeDetail showtime = eventMetadata != null
                ? eventMetadata.getShowtime()
                : null;

        TicketAsset.TicketAssetBuilder builder = TicketAsset.builder()
                .assetCode(UUID.randomUUID().toString())
                .orderItem(item)
                .originalOrderId(order.getId())
                .originalOrderCode(order.getOrderCode())
                .eventId(order.getEventId())
                .ticketTypeId(item.getTicketTypeId())
                .ticketTypeName(item.getTicketTypeName())
                .ticketCode(item.getTicketCode())
                .originalPrice(item.getUnitPrice())
                .originalBuyerId(order.getUserId())
                .currentOwnerId(order.getUserId())
                .accessStatus(TicketAccessStatus.VALID)
                .chainStatus(TicketChainStatus.WEB2_ONLY)
                .tokenId(item.getTokenId())
                .qrSecretHash(null)
                .qrSecretVersion(1)
                .currentResaleListingId(null);

        if (eventMetadata != null) {
            builder.eventName(eventMetadata.getEventName());

            if (showtime != null) {
                builder.showtimeId(showtime.getShowtimeId())
                        .eventStartTime(showtime.getStartDatetime())
                        .eventEndTime(showtime.getEndDatetime())
                        .venueName(showtime.getVenue())
                        .venueAddress(showtime.getFullAddress() != null
                                ? showtime.getFullAddress()
                                : showtime.getAddress());
            } else {
                builder.eventStartTime(eventMetadata.getEventStartTime())
                        .eventEndTime(eventMetadata.getEventEndTime())
                        .venueName(eventMetadata.getVenue())
                        .venueAddress(eventMetadata.getAddress());
            }
        }

        return builder.build();
    }

    private TicketAssetResponse toTicketAssetResponse(TicketAsset asset, Long currentUserId, LocalDateTime now) {
        EligibilityDecision eligibility = evaluateResaleEligibility(asset, currentUserId, now);

        return TicketAssetResponse.builder()
                .ticketAssetId(asset.getId())
                .assetCode(asset.getAssetCode())
                .ticketCode(asset.getTicketCode())
                .originalOrderId(asset.getOriginalOrderId())
                .originalOrderCode(asset.getOriginalOrderCode())
                .eventId(asset.getEventId())
                .eventName(asset.getEventName())
                .showtimeId(asset.getShowtimeId())
                .eventStartTime(asset.getEventStartTime())
                .eventEndTime(asset.getEventEndTime())
                .venueName(asset.getVenueName())
                .venueAddress(asset.getVenueAddress())
                .ticketTypeId(asset.getTicketTypeId())
                .ticketTypeName(asset.getTicketTypeName())
                .originalPrice(asset.getOriginalPrice())
                .accessStatus(asset.getAccessStatus())
                .chainStatus(asset.getChainStatus())
                .tokenId(asset.getTokenId())
                .txHash(asset.getTxHash())
                .contractAddress(asset.getContractAddress())
                .currentResaleListingId(asset.getCurrentResaleListingId())
                .usedAt(asset.getUsedAt())
                .createdAt(asset.getCreatedAt())
                .updatedAt(asset.getUpdatedAt())
                .qrAvailable(isQrAvailable(asset, currentUserId))
                .canResell(eligibility.canResell())
                .resaleBlockedReason(eligibility.canResell() ? null : eligibility.reasonMessage())
                .build();
    }

    private boolean isQrAvailable(TicketAsset asset, Long currentUserId) {
        return asset.getAccessStatus() == TicketAccessStatus.VALID
                && asset.getUsedAt() == null
                && currentUserId.equals(asset.getCurrentOwnerId());
    }

    private EligibilityDecision evaluateResaleEligibility(TicketAsset asset, Long currentUserId, LocalDateTime now) {
        if (!currentUserId.equals(asset.getCurrentOwnerId())) {
            return new EligibilityDecision(false, "NOT_OWNER", "Ticket is not owned by current user");
        }

        if (asset.getAccessStatus() != TicketAccessStatus.VALID) {
            return new EligibilityDecision(false, "INVALID_ACCESS_STATUS",
                    "Ticket access status does not allow resale");
        }

        if (asset.getUsedAt() != null) {
            return new EligibilityDecision(false, "ALREADY_USED", "Ticket has already been used");
        }

        if (asset.getCurrentResaleListingId() != null) {
            return new EligibilityDecision(false, "ALREADY_ON_SALE", "Ticket is already on sale");
        }

        if (asset.getEventEndTime() != null && asset.getEventEndTime().isBefore(now)) {
            return new EligibilityDecision(false, "EVENT_ENDED", "Event has already ended");
        }

        return new EligibilityDecision(true, "ELIGIBLE", "Ticket is eligible for resale");
    }

    private boolean isActiveTicket(TicketAsset asset) {
        return asset.getAccessStatus() == TicketAccessStatus.VALID && asset.getUsedAt() == null;
    }

    private boolean isUsedTicket(TicketAsset asset) {
        return asset.getAccessStatus() == TicketAccessStatus.USED || asset.getUsedAt() != null;
    }

    private boolean isOnSaleTicket(TicketAsset asset) {
        return asset.getAccessStatus() == TicketAccessStatus.LOCKED_RESALE
                || asset.getCurrentResaleListingId() != null;
    }

    private String getDisplayStatusName(TicketAsset asset) {
        if (isOnSaleTicket(asset)) return "On Sale";
        if (isUsedTicket(asset)) return "Used";
        if (asset.getChainStatus() == TicketChainStatus.MINT_PENDING) return "Mint Pending";
        return "Active";
    }

    private String determineTicketStatus(TicketAsset asset) {
        if (isOnSaleTicket(asset)) return "on_sale";
        if (isUsedTicket(asset)) return "used";
        if (asset.getChainStatus() == TicketChainStatus.MINT_PENDING) return "minting";
        return "active";
    }

    private record EligibilityDecision(boolean canResell, String reasonCode, String reasonMessage) {
    }

    @Transactional
    public void handleWeb3MintWebhook(Web3MintWebhookRequest request) {
        if (request.getData() == null) {
            log.warn("Web3 mint webhook received without data. JobId: {}", request.getJobId());
            return;
        }

        // Handle order-level callback
        if (request.getData().getOrderId() != null && request.getData().getTickets() != null) {
            log.info("Received order-level mint webhook for orderId: {}, status: {}", request.getData().getOrderId(), request.getStatus());
            for (Web3MintWebhookRequest.TicketResult ticketResult : request.getData().getTickets()) {
                boolean isSuccess = ticketResult.getTxHash() != null && ticketResult.getError() == null;
                handleSingleTicketResult(
                        ticketResult.getTicketCode(),
                        ticketResult.getTokenId(),
                        ticketResult.getTxHash(),
                        ticketResult.getChainCommand(),
                        isSuccess,
                        ticketResult.getError()
                );
            }
            return;
        }

        // Handle single ticket callback
        if (request.getData().getTicketCode() != null) {
            boolean isSuccess = "success".equals(request.getStatus());
            handleSingleTicketResult(
                    request.getData().getTicketCode(),
                    request.getData().getTokenId(),
                    request.getTxHash(),
                    request.getData().getChainCommand(),
                    isSuccess,
                    request.getError()
            );
            return;
        }

        log.warn("Web3 mint webhook received without ticketCode or orderId. JobId: {}", request.getJobId());
    }

    private void handleSingleTicketResult(String ticketCode, String tokenId, String txHash, Web3MintWebhookRequest.ChainCommand chainCommand, boolean isSuccess, String error) {
        if (ticketCode == null) return;
        
        Optional<TicketAsset> assetOpt = ticketAssetRepository.findByTicketCodeOrAssetCode(ticketCode, ticketCode);
        if (assetOpt.isEmpty()) {
            log.warn("Ticket not found for code: {}", ticketCode);
            return;
        }
        
        TicketAsset asset = assetOpt.get();

        if (isSuccess) {
            asset.setChainStatus(TicketChainStatus.MINTED);
            if (txHash != null) {
                asset.setTxHash(txHash);
            }
            if (tokenId != null) {
                asset.setTokenId(tokenId);
            }
            if (chainCommand != null) {
                asset.setToWallet(chainCommand.getToWallet());
                if (chainCommand.getMetadataURI() != null) {
                    asset.setMetadataUri(chainCommand.getMetadataURI());
                }
            }
            log.info("Successfully updated web3 mint info for ticket: {}. TxHash: {}", ticketCode, txHash);
        } else {
            asset.setChainStatus(TicketChainStatus.MINT_FAILED);
            log.error("Web3 mint failed for ticket: {}. Error: {}", ticketCode, error);
        }

        ticketAssetRepository.save(asset);
    }
}
