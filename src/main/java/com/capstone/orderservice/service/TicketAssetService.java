package com.capstone.orderservice.service;

import com.capstone.orderservice.client.EventDetailInternalResponse;
import com.capstone.orderservice.client.InventoryFeignClient;
import com.capstone.orderservice.client.WorkerClient;
import com.capstone.orderservice.dto.BaseResponse;
import com.capstone.orderservice.dto.response.MyTicketGroupResponse;
import com.capstone.orderservice.dto.response.MyTicketItemResponse;
import com.capstone.orderservice.dto.response.ResaleEligibilityResponse;
import com.capstone.orderservice.dto.response.TicketAssetResponse;
import com.capstone.orderservice.dto.response.VerifyOwnershipResponse;
import com.capstone.orderservice.dto.response.WithdrawResponse;
import com.capstone.orderservice.dto.request.Web3MintWebhookRequest;
import com.capstone.orderservice.dto.request.Web3TransferWebhookRequest;
import com.capstone.orderservice.dto.request.Web3BatchCheckInWebhookRequest;
import com.capstone.orderservice.dto.request.Web3WithdrawWebhookRequest;
import com.capstone.orderservice.dto.event.TicketUsedEvent;
import com.capstone.orderservice.entity.Order;
import com.capstone.orderservice.entity.OrderItem;
import com.capstone.orderservice.entity.TicketAsset;
import com.capstone.orderservice.enums.OrderStatus;
import com.capstone.orderservice.enums.OrderType;
import com.capstone.orderservice.enums.TicketAccessStatus;
import com.capstone.orderservice.enums.TicketChainStatus;
import com.capstone.orderservice.exception.AppException;
import com.capstone.orderservice.exception.ErrorCode;
import com.capstone.orderservice.dto.event.TicketAccessSyncEvent;
import com.capstone.orderservice.producer.RedisStreamProducer;
import com.capstone.orderservice.repository.TicketAssetRepository;
import com.capstone.orderservice.repository.ResaleListingRepository;
import com.capstone.orderservice.entity.ResaleListing;
import com.capstone.orderservice.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TicketAssetService {
    private static final BigDecimal ORGANIZER_ROYALTY_RATE = BigDecimal.ZERO;

    @Value("${evoticket.platform-fee-rate:0.02}")
    private BigDecimal platformFeeRate;

    @Value("${evoticket.price-cap-multiplier:1.10}")
    private BigDecimal priceCapMultiplier;

    private final TicketAssetRepository ticketAssetRepository;
    private final InventoryFeignClient inventoryFeignClient;
    private final JwtUtil jwtUtil;
    private final TicketProvenanceService ticketProvenanceService;
    private final ResaleListingRepository resaleListingRepository;
    private final RedisStreamProducer redisStreamProducer;
    private final WorkerClient workerClient;

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
            syncTicketAccess(savedAsset);
        }
    }

    @Transactional(readOnly = true)
    public List<MyTicketGroupResponse> getMyTickets() {
        Long currentUserId = jwtUtil.getDataFromAuth().userId();
        List<TicketAsset> assets = ticketAssetRepository.findByCurrentOwnerId(currentUserId);

        List<Long> resaleListingIds = assets.stream()
                .map(TicketAsset::getCurrentResaleListingId)
                .filter(Objects::nonNull)
                .toList();

        Map<Long, ResaleListing> listingMap = resaleListingRepository.findAllById(resaleListingIds).stream()
                .collect(Collectors.toMap(ResaleListing::getId, listing -> listing));

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
                
                ResaleListing listing = asset.getCurrentResaleListingId() != null 
                    ? listingMap.get(asset.getCurrentResaleListingId()) 
                    : null;

                return MyTicketItemResponse.builder()
                        .id(asset.getId())
                        .ticketName(asset.getTicketTypeName())
                        .ticketType(asset.getTicketTypeName())
                        .seat(asset.getTicketTypeName())
                        .ticketCode(asset.getTicketCode())
                        .tokenId(asset.getTokenId())
                        .contractAddress(asset.getContractAddress())
                        .fromBlock(asset.getFromBlock())
                        .toBlock(asset.getToBlock())
                        .ticketAccessStatus(asset.getAccessStatus())
                        .ticketChainStatus(asset.getChainStatus())
                        .listingCode(listing != null ? listing.getListingCode() : null)
                        .listingPrice(listing != null ? listing.getListingPrice() : null)
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
                    .createdAt(firstAsset.getOrderItem().getOrder().getCreatedAt())
                    .tickets(tickets)
                    .build();
        }).sorted(Comparator.comparing(MyTicketGroupResponse::getCreatedAt).reversed())
                .toList();
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

        BigDecimal actualPriceCapMultiplier = priceCapMultiplier;
        BigDecimal actualRoyaltyRate = ORGANIZER_ROYALTY_RATE;

        Optional<EventDetailInternalResponse> eventMetadata = fetchEventMetadata(asset.getTicketTypeId());
        if (eventMetadata.isPresent()) {
            EventDetailInternalResponse metadata = eventMetadata.get();
            if (metadata.getMaxResalePricePercentage() != null) {
                actualPriceCapMultiplier = metadata.getMaxResalePricePercentage();
            }
            if (metadata.getOrganizerRoyaltyFeePercentage() != null) {
                actualRoyaltyRate = metadata.getOrganizerRoyaltyFeePercentage();
            }
        }

        BigDecimal priceCap = originalPrice.multiply(actualPriceCapMultiplier).setScale(2, java.math.RoundingMode.HALF_UP);

        return ResaleEligibilityResponse.builder()
                .ticketAssetId(asset.getId())
                .canResell(decision.canResell())
                .reasonCode(decision.reasonCode())
                .reasonMessage(decision.reasonMessage())
                .originalPrice(asset.getOriginalPrice())
                .priceCap(priceCap)
                .platformFeeRate(platformFeeRate)
                .organizerRoyaltyRate(actualRoyaltyRate)
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
                .chainStatus(TicketChainStatus.MINT_PENDING)
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

            builder.category(eventMetadata.getCategory())
                   .provinceCode(eventMetadata.getShowtime().getProvinceCode());
        }

        return builder.build();
    }

    private TicketAssetResponse toTicketAssetResponse(TicketAsset asset, Long currentUserId, LocalDateTime now) {
        EligibilityDecision eligibility = evaluateResaleEligibility(asset, currentUserId, now);

        BigDecimal actualPriceCapMultiplier = priceCapMultiplier;
        BigDecimal actualRoyaltyRate = ORGANIZER_ROYALTY_RATE;
        Optional<EventDetailInternalResponse> eventMetadata = fetchEventMetadata(asset.getTicketTypeId());
        if (eventMetadata.isPresent()) {
            EventDetailInternalResponse metadata = eventMetadata.get();
            if (metadata.getMaxResalePricePercentage() != null) {
                actualPriceCapMultiplier = metadata.getMaxResalePricePercentage();
            }
            if (metadata.getOrganizerRoyaltyFeePercentage() != null) {
                actualRoyaltyRate = metadata.getOrganizerRoyaltyFeePercentage();
            }
        }
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
                .fromBlock(asset.getFromBlock())
                .toBlock(asset.getToBlock())
                .currentResaleListingId(asset.getCurrentResaleListingId())
                .usedAt(asset.getUsedAt())
                .createdAt(asset.getCreatedAt())
                .updatedAt(asset.getUpdatedAt())
                .qrAvailable(isQrAvailable(asset, currentUserId))
                .canResell(eligibility.canResell())
                .resaleBlockedReason(eligibility.canResell() ? null : eligibility.reasonMessage())
                .platformFeeRate(platformFeeRate)
                .maxResalePricePercentage(actualPriceCapMultiplier)
                .organizerRoyaltyFeePercentage(actualRoyaltyRate)
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

        if (asset.getEventStartTime() != null && asset.getEventStartTime().minusHours(3).isBefore(now)) {
            return new EligibilityDecision(false, "TOO_CLOSE_TO_EVENT", "Cannot resell ticket within 3 hours of event start time");
        }

        return new EligibilityDecision(true, "ELIGIBLE", "Ticket is eligible for resale");
    }

    private boolean isUsedTicket(TicketAsset asset) {
        return asset.getAccessStatus() == TicketAccessStatus.CHECKED_IN
                || asset.getAccessStatus() == TicketAccessStatus.USED
                || asset.getUsedAt() != null;
    }

    private boolean isOnSaleTicket(TicketAsset asset) {
        return asset.getAccessStatus() == TicketAccessStatus.LOCKED_RESALE
                || asset.getCurrentResaleListingId() != null;
    }

    private String getDisplayStatusName(TicketAsset asset) {
        if (isOnSaleTicket(asset)) return "On Sale";
        if (isUsedTicket(asset)) return "Checked In";
        if (asset.getChainStatus() == TicketChainStatus.MINT_PENDING) return "Mint Pending";
        return "Active";
    }


    private record EligibilityDecision(boolean canResell, String reasonCode, String reasonMessage) {
    }

    @Transactional
    public void handleWeb3MintWebhook(Web3MintWebhookRequest request) {
        // Support flat structure for order-level callback
        String orderId = request.getOrderId() != null ? request.getOrderId() : (request.getData() != null ? request.getData().getOrderId() : null);
        List<Web3MintWebhookRequest.TicketResult> tickets = request.getTickets() != null ? request.getTickets() : (request.getData() != null ? request.getData().getTickets() : null);

        if (orderId != null && tickets != null) {
            log.info("Received order-level mint webhook for orderId: {}, status: {}", orderId, request.getStatus());
            for (Web3MintWebhookRequest.TicketResult ticketResult : tickets) {
                boolean isSuccess = ticketResult.getTxHash() != null && ticketResult.getError() == null;
                handleSingleTicketResult(
                        ticketResult.getTicketCode(),
                        ticketResult.getTokenId(),
                        ticketResult.getTxHash(),
                        ticketResult.getBlockNumber(), // fromBlock = blockNumber
                        ticketResult.getBlockNumber(), // toBlock = blockNumber
                        ticketResult.getContractAddress(),
                        ticketResult.getChainCommand(),
                        isSuccess,
                        ticketResult.getError()
                );
            }
            return;
        }

        if (request.getData() == null) {
            log.warn("Web3 mint webhook received without data. JobId: {}", request.getJobId());
            return;
        }

        // Handle single ticket callback
        if (request.getData().getTicketCode() != null) {
            boolean isSuccess = "success".equals(request.getStatus());
            handleSingleTicketResult(
                    request.getData().getTicketCode(),
                    request.getData().getTokenId(),
                    request.getTxHash(),
                    request.getBlockNumber(), // fromBlock
                    request.getBlockNumber(), // toBlock
                    request.getContractAddress() != null ? request.getContractAddress() : request.getData().getContractAddress(),
                    request.getData().getChainCommand(),
                    isSuccess,
                    request.getError()
            );
            return;
        }

        log.warn("Web3 mint webhook received without ticketCode or orderId. JobId: {}", request.getJobId());
    }

    @Transactional
    public void handleWeb3TransferWebhook(Web3TransferWebhookRequest request) {
        if (request.getData() == null) {
            log.warn("Web3 transfer webhook received without data. JobId: {}", request.getJobId());
            return;
        }

        String tokenId = request.getData().getTokenId();
        if (tokenId == null) {
            log.warn("Web3 transfer webhook received without tokenId. JobId: {}", request.getJobId());
            return;
        }

        Optional<TicketAsset> assetOpt = ticketAssetRepository.findByTokenId(tokenId);
        if (assetOpt.isEmpty()) {
            log.warn("Ticket not found for tokenId: {}", tokenId);
            return;
        }

        TicketAsset asset = assetOpt.get();
        boolean isSuccess = "success".equals(request.getStatus());

        if (isSuccess) {
            asset.setChainStatus(TicketChainStatus.TRANSFERRED);
            if (request.getTxHash() != null) {
                asset.setTxHash(request.getTxHash());
            }
            if (request.getBlockNumber() != null) {
                asset.setToBlock(request.getBlockNumber());
            }
            if (request.getAddressContract() != null) {
                asset.setContractAddress(request.getAddressContract());
            }

            // Sync with OrderItem
            if (asset.getOrderItem() != null) {
                OrderItem item = asset.getOrderItem();
                if (request.getBlockNumber() != null) item.setToBlock(request.getBlockNumber());
                if (request.getAddressContract() != null) item.setContractAddress(request.getAddressContract());
            }

            Long oldOwnerId = asset.getCurrentOwnerId();
            // Update owner if possible
            Long newOwnerId = null;
            if (request.getData().getTo_userID() != null) {
                try {
                    newOwnerId = Long.parseLong(request.getData().getTo_userID());
                    asset.setCurrentOwnerId(newOwnerId);
                } catch (NumberFormatException e) {
                    log.warn("Failed to parse to_userID: {}", request.getData().getTo_userID());
                }
            }

            try {
                Map<String, Object> transferEvent = new HashMap<>();
                transferEvent.put("ticketCode", asset.getTicketCode());
                transferEvent.put("eventName", asset.getEventName());
                transferEvent.put("ticketTypeName", asset.getTicketTypeName());
                transferEvent.put("fromUserId", oldOwnerId);
                transferEvent.put("toUserId", newOwnerId);
                redisStreamProducer.sendMessage("ticket-transferred", transferEvent);
            } catch (Exception e) {
                log.error("Failed to send ticket-transferred stream message", e);
            }

            log.info("Successfully updated web3 transfer info for ticket: {}. TxHash: {}", tokenId, request.getTxHash());
        } else {
            asset.setChainStatus(TicketChainStatus.TRANSFER_FAILED);
            log.error("Web3 transfer failed for ticket: {}. Error: {}", tokenId, request.getError());
        }

        TicketAsset savedAsset = ticketAssetRepository.save(asset);
        syncTicketAccess(savedAsset);
    }

    private void handleSingleTicketResult(String ticketCode, String tokenId, String txHash, Long fromBlock, Long toBlock, String contractAddress, Web3MintWebhookRequest.ChainCommand chainCommand, boolean isSuccess, String error) {
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
            if (fromBlock != null) {
                asset.setFromBlock(fromBlock);
            }
            if (toBlock != null) {
                asset.setToBlock(toBlock);
            }
            if (contractAddress != null) {
                asset.setContractAddress(contractAddress);
            }
            if (chainCommand != null) {
                asset.setToWallet(chainCommand.getToWallet());
                if (chainCommand.getMetadataURI() != null) {
                    asset.setMetadataUri(chainCommand.getMetadataURI());
                }
            }

            // Sync with OrderItem
            if (asset.getOrderItem() != null) {
                OrderItem item = asset.getOrderItem();
                if (tokenId != null) item.setTokenId(tokenId);
                if (fromBlock != null) item.setFromBlock(fromBlock);
                if (toBlock != null) item.setToBlock(toBlock);
                if (contractAddress != null) item.setContractAddress(contractAddress);
            }

            try {
                Map<String, Object> mintEvent = new HashMap<>();
                mintEvent.put("userId", asset.getCurrentOwnerId());
                mintEvent.put("ticketCode", asset.getTicketCode());
                mintEvent.put("eventName", asset.getEventName());
                mintEvent.put("ticketTypeName", asset.getTicketTypeName());
                redisStreamProducer.sendMessage("ticket-minted", mintEvent);
            } catch (Exception e) {
                log.error("Failed to send ticket-minted stream message", e);
            }

            log.info("Successfully updated web3 mint info for ticket: {}. TxHash: {}", ticketCode, txHash);
        } else {
            asset.setChainStatus(TicketChainStatus.MINT_FAILED);
            log.error("Web3 mint failed for ticket: {}. Error: {}", ticketCode, error);
        }

        TicketAsset savedAsset = ticketAssetRepository.save(asset);
        syncTicketAccess(savedAsset);
    }

    public void syncTicketAccess(TicketAsset asset) {
        try {
            TicketAccessSyncEvent event = TicketAccessSyncEvent.builder()
                    .ticketAssetId(asset.getId())
                    .ticketCode(asset.getTicketCode())
                    .eventId(asset.getEventId())
                    .showtimeId(asset.getShowtimeId())
                    .currentOwnerId(asset.getCurrentOwnerId())
                    .accessStatus(asset.getAccessStatus())
                    .qrVersion(asset.getQrSecretVersion())
                    .ticketTypeName(asset.getTicketTypeName())
                    .gatePolicySnapshot(null) // Can be enriched later from Inventory
                    .build();

            redisStreamProducer.sendMessage("ticket-access-sync", event);
            log.info("Sent sync event for ticketAssetId: {}", asset.getId());
        } catch (Exception e) {
            log.error("Failed to send ticket sync event for assetId: {}", asset.getId(), e);
        }
    }

    @Transactional
    public void handleTicketUsedEvent(TicketUsedEvent event) {
        TicketAsset asset = ticketAssetRepository.findById(event.getTicketAssetId())
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Ticket not found"));

        if (asset.getAccessStatus() == TicketAccessStatus.CHECKED_IN) {
            log.info("Ticket {} is already marked as CHECKED_IN, skipping", event.getTicketAssetId());
            return;
        }

        asset.setAccessStatus(TicketAccessStatus.CHECKED_IN);
        asset.setChainStatus(TicketChainStatus.CHECKED_IN);
        asset.setUsedAt(LocalDateTime.ofInstant(event.getUsedAt(), ZoneId.systemDefault()));
        ticketAssetRepository.save(asset);

        ticketProvenanceService.recordTicketUsed(asset, event.getUsedByCheckerId(), event.getUsedAtGateId());
        log.info("Ticket {} marked as CHECKED_IN via sync event", event.getTicketAssetId());
    }

    @Transactional
    public void handleWeb3CheckInWebhook(Web3BatchCheckInWebhookRequest request) {
        log.info("Handling web3 batch check-in webhook for job: {}, status: {}", request.getJobId(), request.getStatus());
        
        if (request.getTickets() != null) {
            for (Web3BatchCheckInWebhookRequest.TicketResult ticketResult : request.getTickets()) {
                Optional<TicketAsset> assetOpt = Optional.empty();
                if (ticketResult.getTicketCode() != null) {
                    assetOpt = ticketAssetRepository.findByTicketCodeOrAssetCode(ticketResult.getTicketCode(), ticketResult.getTicketCode());
                }
                if (assetOpt.isEmpty() && ticketResult.getTokenId() != null) {
                    assetOpt = ticketAssetRepository.findByTokenId(ticketResult.getTokenId());
                }
                
                if (assetOpt.isPresent()) {
                    TicketAsset asset = assetOpt.get();
                    asset.setChainStatus(TicketChainStatus.CHECKED_IN);
                    if (ticketResult.getTransactionHash() != null) {
                        asset.setTxHash(ticketResult.getTransactionHash());
                    }
                    if (ticketResult.getBlockNumber() != null) {
                        asset.setToBlock(ticketResult.getBlockNumber());
                    }
                    asset.setAccessStatus(TicketAccessStatus.USED);
                    ticketAssetRepository.save(asset);
                    syncTicketAccess(asset);
                    
                    ticketProvenanceService.updateCheckInTxHash(
                            asset.getId(),
                            ticketResult.getTransactionHash(),
                            ticketResult.getBlockNumber(),
                            TicketChainStatus.CHECKED_IN.name()
                    );
                    log.info("Successfully updated checked-in status for ticketCode: {}, tokenId: {}", asset.getTicketCode(), asset.getTokenId());
                } else {
                    log.warn("Ticket not found for code: {} or tokenId: {}", ticketResult.getTicketCode(), ticketResult.getTokenId());
                }
            }
        }
        
        if (request.getErrors() != null) {
            for (Web3BatchCheckInWebhookRequest.TicketError ticketError : request.getErrors()) {
                Optional<TicketAsset> assetOpt = Optional.empty();
                if (ticketError.getTicketCode() != null) {
                    assetOpt = ticketAssetRepository.findByTicketCodeOrAssetCode(ticketError.getTicketCode(), ticketError.getTicketCode());
                }
                if (assetOpt.isEmpty() && ticketError.getTokenId() != null) {
                    assetOpt = ticketAssetRepository.findByTokenId(ticketError.getTokenId());
                }
                
                if (assetOpt.isPresent()) {
                    TicketAsset asset = assetOpt.get();
                    asset.setChainStatus(TicketChainStatus.CHECKIN_FAILED);
                    ticketAssetRepository.save(asset);
                    
                    ticketProvenanceService.updateCheckInTxHash(
                            asset.getId(),
                            null,
                            null,
                            TicketChainStatus.CHECKIN_FAILED.name()
                    );
                    log.error("Failed checked-in sync for ticketCode: {}, error: {}", asset.getTicketCode(), ticketError.getError());
                } else {
                    log.warn("Failed ticket check-in not found for code: {} or tokenId: {}", ticketError.getTicketCode(), ticketError.getTokenId());
                }
            }
        }
    }

    public boolean verifyTicketOwnership(Long ticketAssetId, Long currentOwnerId) {
        try {
            TicketAsset asset = ticketAssetRepository.findById(ticketAssetId)
                    .orElseThrow(() -> new com.capstone.orderservice.exception.AppException(
                            com.capstone.orderservice.exception.ErrorCode.RESOURCE_NOT_FOUND, "Ticket not found"));

            String tokenIdStr = asset.getTokenId();
            if (tokenIdStr == null || tokenIdStr.isBlank()) {
                log.warn("TicketAsset {} has no tokenId, cannot verify ownership on-chain", ticketAssetId);
                return false;
            }

            Long tokenId = Long.parseLong(tokenIdStr);

            VerifyOwnershipResponse response = workerClient.verifyOwnership(tokenId, currentOwnerId.toString());
            return response != null && "owned".equalsIgnoreCase(response.getStatus());
        } catch (Exception e) {
            log.error("Error verifying ticket ownership for ticketAssetId: {} and owner: {}", ticketAssetId, currentOwnerId, e);
            return false;
        }
    }

    @Transactional
    public WithdrawResponse withdrawTicket(Long tokenId, String personalWallet) {
        Long currentUserId = jwtUtil.getDataFromAuth().userId();
        
        TicketAsset asset = ticketAssetRepository.findByTokenId(tokenId.toString())
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy vé với tokenId: " + tokenId));
                
        if (!currentUserId.equals(asset.getCurrentOwnerId())) {
            throw new AppException(ErrorCode.FORBIDDEN, "Bạn không sở hữu vé này.");
        }
        
        if (asset.getAccessStatus() != TicketAccessStatus.USED) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Chỉ những vé đã check-in mới có thể rút.");
        }

        asset.setChainStatus(TicketChainStatus.WITHDRAW_PENDING);
        ticketAssetRepository.save(asset);
        
        return workerClient.withdrawTicket(tokenId, personalWallet, currentUserId.toString());
    }

    @Transactional
    public void handleWeb3WithdrawWebhook(Web3WithdrawWebhookRequest request) {
        log.info("Handling web3 withdraw webhook for job: {}, status: {}", request.getJobId(), request.getStatus());
        
        Long tokenId = null;
        if (request.getData() != null) {
            tokenId = request.getData().getTokenId();
        }
        
        if (tokenId == null) {
            log.warn("Web3 withdraw webhook received without tokenId. JobId: {}", request.getJobId());
            return;
        }
        
        Optional<TicketAsset> assetOpt = ticketAssetRepository.findByTokenId(tokenId.toString());
        if (assetOpt.isEmpty()) {
            log.warn("Ticket not found for tokenId: {}", tokenId);
            return;
        }
        
        TicketAsset asset = assetOpt.get();
        boolean isSuccess = "success".equalsIgnoreCase(request.getStatus());
        
        if (isSuccess) {
            asset.setAccessStatus(TicketAccessStatus.WITHDRAWN);
            asset.setChainStatus(TicketChainStatus.WITHDRAWN);
            if (request.getBlockNumber() != null) {
                asset.setToBlock(request.getBlockNumber());
            }
            log.info("Successfully processed withdraw webhook (success) for tokenId: {}", tokenId);
        } else {
            asset.setAccessStatus(TicketAccessStatus.CHECKED_IN);
            log.warn("Withdraw failed for tokenId: {}. Error: {}", tokenId, request.getError());
        }
        
        TicketAsset savedAsset = ticketAssetRepository.save(asset);
        syncTicketAccess(savedAsset);
    }
}
