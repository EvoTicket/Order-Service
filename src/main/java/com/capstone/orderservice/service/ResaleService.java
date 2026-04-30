package com.capstone.orderservice.service;

import com.capstone.orderservice.client.PaymentFeignClient;
import com.capstone.orderservice.client.PaymentTransactionResponse;
import com.capstone.orderservice.dto.BaseResponse;
import com.capstone.orderservice.dto.request.CreateResaleListingRequest;
import com.capstone.orderservice.dto.request.ResaleCheckoutRequest;
import com.capstone.orderservice.dto.request.ResaleQuoteRequest;
import com.capstone.orderservice.dto.event.PaymentSuccessEvent;
import com.capstone.orderservice.dto.response.PaymentLinkResponse;
import com.capstone.orderservice.dto.response.ResaleCheckoutResponse;
import com.capstone.orderservice.dto.response.ResaleListingResponse;
import com.capstone.orderservice.dto.response.ResalePaymentStatusResponse;
import com.capstone.orderservice.dto.response.ResaleQuoteResponse;
import com.capstone.orderservice.entity.Order;
import com.capstone.orderservice.entity.OrderItem;
import com.capstone.orderservice.entity.ResaleListing;
import com.capstone.orderservice.entity.TicketAsset;
import com.capstone.orderservice.enums.OrderStatus;
import com.capstone.orderservice.enums.OrderType;
import com.capstone.orderservice.enums.ResalePaymentResultStatus;
import com.capstone.orderservice.enums.ResaleListingStatus;
import com.capstone.orderservice.enums.TicketAccessStatus;
import com.capstone.orderservice.exception.AppException;
import com.capstone.orderservice.exception.ErrorCode;
import com.capstone.orderservice.repository.OrderRepository;
import com.capstone.orderservice.repository.ResaleListingRepository;
import com.capstone.orderservice.repository.TicketAssetRepository;
import com.capstone.orderservice.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
@Slf4j
public class ResaleService {
    private static final List<ResaleListingStatus> ACTIVE_LISTING_STATUSES = List.of(
            ResaleListingStatus.ACTIVE,
            ResaleListingStatus.PAYMENT_PENDING
    );

    private final TicketAssetRepository ticketAssetRepository;
    private final ResaleListingRepository resaleListingRepository;
    private final ResalePricingService resalePricingService;
    private final TicketProvenanceService ticketProvenanceService;
    private final OrderRepository orderRepository;
    private final JwtUtil jwtUtil;
    private final PaymentFeignClient paymentFeignClient;

    @org.springframework.beans.factory.annotation.Autowired
    @org.springframework.context.annotation.Lazy
    private ResaleService self;

    @Transactional(readOnly = true)
    public ResaleQuoteResponse quote(ResaleQuoteRequest request) {
        Long currentUserId = jwtUtil.getDataFromAuth().userId();
        TicketAsset asset = ticketAssetRepository.findByIdAndCurrentOwnerId(request.getTicketAssetId(), currentUserId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Ticket not found"));

        ResalePricingService.ResalePricing pricing = resalePricingService.calculate(
                asset.getOriginalPrice(),
                request.getListingPrice()
        );
        ResaleDecision decision = evaluateListingRequest(asset, request.getListingPrice(), pricing, LocalDateTime.now());

        return toQuoteResponse(asset, pricing, decision);
    }

    @Transactional
    public ResaleListingResponse createListing(CreateResaleListingRequest request) {
        Long currentUserId = jwtUtil.getDataFromAuth().userId();
        TicketAsset asset = ticketAssetRepository.findByIdForUpdate(request.getTicketAssetId())
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Ticket not found"));

        if (!currentUserId.equals(asset.getCurrentOwnerId())) {
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Ticket not found");
        }

        ResalePricingService.ResalePricing pricing = resalePricingService.calculate(
                asset.getOriginalPrice(),
                request.getListingPrice()
        );
        ResaleDecision decision = evaluateListingRequest(asset, request.getListingPrice(), pricing, LocalDateTime.now());
        if (!decision.valid()) {
            throw new AppException(ErrorCode.BAD_REQUEST, decision.reasonMessage());
        }

        boolean hasActiveListing = resaleListingRepository.existsByTicketAsset_IdAndStatusIn(
                asset.getId(),
                ACTIVE_LISTING_STATUSES
        );
        if (hasActiveListing) {
            throw new AppException(ErrorCode.CONFLICT, "Ticket already has an active resale listing");
        }

        ResaleListing listing = ResaleListing.builder()
                .listingCode(generateListingCode())
                .ticketAsset(asset)
                .sellerId(currentUserId)
                .buyerId(null)
                .paymentOrder(null)
                .originalPrice(pricing.originalPrice())
                .listingPrice(pricing.listingPrice())
                .priceCap(pricing.priceCap())
                .platformFeeAmount(pricing.platformFeeAmount())
                .organizerRoyaltyAmount(pricing.organizerRoyaltyAmount())
                .sellerPayoutAmount(pricing.sellerPayoutAmount())
                .status(ResaleListingStatus.ACTIVE)
                .build();

        ResaleListing savedListing = resaleListingRepository.saveAndFlush(listing);

        asset.setAccessStatus(TicketAccessStatus.LOCKED_RESALE);
        asset.setCurrentResaleListingId(savedListing.getId());
        ticketAssetRepository.save(asset);

        ticketProvenanceService.recordResaleListed(asset, savedListing);

        return ResaleListingResponse.fromEntity(savedListing);
    }

    @Transactional
    public ResaleListingResponse cancelListing(String listingCode) {
        Long currentUserId = jwtUtil.getDataFromAuth().userId();
        ResaleListing listing = resaleListingRepository.findByListingCodeForUpdate(listingCode)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Resale listing not found"));

        if (!currentUserId.equals(listing.getSellerId())) {
            throw new AppException(ErrorCode.FORBIDDEN, "You cannot cancel this resale listing");
        }

        if (listing.getStatus() != ResaleListingStatus.ACTIVE) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Only active resale listings can be cancelled");
        }

        TicketAsset asset = ticketAssetRepository.findByIdForUpdate(listing.getTicketAsset().getId())
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Ticket not found"));

        LocalDateTime now = LocalDateTime.now();
        listing.setStatus(ResaleListingStatus.CANCELLED);
        listing.setCancelledAt(now);

        asset.setAccessStatus(TicketAccessStatus.VALID);
        asset.setCurrentResaleListingId(null);
        ticketAssetRepository.save(asset);

        ResaleListing savedListing = resaleListingRepository.save(listing);
        ticketProvenanceService.recordResaleCancelled(asset, savedListing);

        return ResaleListingResponse.fromEntity(savedListing);
    }

    @Transactional(readOnly = true)
    public Page<ResaleListingResponse> getActiveListings(
            Long eventId,
            Long ticketTypeId,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Pageable pageable
    ) {
        return resaleListingRepository.findActiveListings(
                        ResaleListingStatus.ACTIVE,
                        eventId,
                        ticketTypeId,
                        minPrice,
                        maxPrice,
                        pageable
                )
                .map(ResaleListingResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public ResaleListingResponse getActiveListingDetail(String listingCode) {
        ResaleListing listing = resaleListingRepository.findByListingCode(listingCode)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Resale listing not found"));

        if (listing.getStatus() != ResaleListingStatus.ACTIVE) {
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Resale listing not found");
        }

        return ResaleListingResponse.fromEntity(listing);
    }

    public ResaleCheckoutResponse checkout(String listingCode, ResaleCheckoutRequest request) {
        ResaleService transactionalService = self != null ? self : this;
        ResaleCheckoutResponse response = transactionalService.createResaleCheckout(listingCode, request);

        try {
            PaymentLinkResponse paymentLink = createPaymentLinkOrThrow(response.getOrderCode());
            response.setRedirectUrl(paymentLink.getRedirectUrl());
            return response;
        } catch (Exception e) {
            try {
                transactionalService.restoreFailedPaymentInitialization(response.getOrderCode());
            } catch (Exception restoreException) {
                log.error("Failed to restore resale checkout after payment initialization failure for order {}",
                        response.getOrderCode(), restoreException);
            }
            throw new AppException(ErrorCode.PAYMENT_GATEWAY_ERROR, "Unable to initialize resale payment", e);
        }
    }

    @Transactional
    public ResaleCheckoutResponse createResaleCheckout(String listingCode, ResaleCheckoutRequest request) {
        Long buyerId = jwtUtil.getDataFromAuth().userId();
        ResaleListing listing = resaleListingRepository.findByListingCodeForUpdate(listingCode)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Resale listing not found"));

        if (listing.getStatus() != ResaleListingStatus.ACTIVE) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Only active resale listings can be checked out");
        }

        if (buyerId.equals(listing.getSellerId())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Seller cannot checkout their own resale listing");
        }

        TicketAsset asset = ticketAssetRepository.findByIdForUpdate(listing.getTicketAsset().getId())
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Ticket not found"));

        validateListingStillCheckoutable(listing, asset);

        Order resaleOrder = buildResaleOrder(request, buyerId, listing, asset);
        Order savedOrder = orderRepository.saveAndFlush(resaleOrder);

        listing.setStatus(ResaleListingStatus.PAYMENT_PENDING);
        listing.setBuyerId(buyerId);
        listing.setPaymentOrder(savedOrder);
        ResaleListing savedListing = resaleListingRepository.save(listing);

        return ResaleCheckoutResponse.builder()
                .listingCode(savedListing.getListingCode())
                .listingId(savedListing.getId())
                .ticketAssetId(asset.getId())
                .orderId(savedOrder.getId())
                .orderCode(savedOrder.getOrderCode())
                .orderType(savedOrder.getOrderType())
                .orderStatus(savedOrder.getOrderStatus())
                .paymentMethod(savedOrder.getPaymentMethod())
                .amount(savedOrder.getFinalAmount())
                .listingPrice(savedListing.getListingPrice())
                .platformFeeAmount(savedListing.getPlatformFeeAmount())
                .sellerPayoutAmount(savedListing.getSellerPayoutAmount())
                .status(savedListing.getStatus())
                .message("Resale checkout order created. Awaiting payment.")
                .build();
    }

    @Transactional
    public void restoreFailedPaymentInitialization(String orderCode) {
        Order order = orderRepository.findByOrderCodeForUpdate(orderCode)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Order not found"));
        if (!isResaleOrder(order)) {
            return;
        }

        Optional<ResaleListing> listingOptional = resaleListingRepository.findByPaymentOrder_IdForUpdate(order.getId());
        if (listingOptional.isPresent()) {
            ResaleListing listing = listingOptional.get();
            if (listing.getStatus() == ResaleListingStatus.SOLD) {
                return;
            }

            if (listing.getStatus() == ResaleListingStatus.PAYMENT_PENDING) {
                OrderStatus restoredStatus = isNonPayableTerminalOrder(order.getOrderStatus())
                        ? order.getOrderStatus()
                        : OrderStatus.PAYMENT_FAILED;
                restorePendingResaleCheckout(order, listing, restoredStatus);
                return;
            }
        }

        if (order.getOrderStatus() == OrderStatus.PENDING) {
            order.setOrderStatus(OrderStatus.PAYMENT_FAILED);
            orderRepository.save(order);
        }
    }

    @Transactional(readOnly = true)
    public ResalePaymentStatusResponse getPaymentStatus(String orderCode) {
        Order order = orderRepository.findByOrderCode(orderCode)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Order not found"));
        ensureResaleOrder(order);

        ResaleListing listing = findListingForStatus(order);
        TicketAsset asset = findTicketAssetForOrder(order, listing);
        validateCanViewResalePayment(order, listing, asset);

        boolean hasPaymentTransaction = order.getOrderStatus() == OrderStatus.PAYMENT_FAILED
                && paymentTransactionExists(order.getOrderCode());
        return buildPaymentStatusResponse(order, listing, asset, null, hasPaymentTransaction);
    }

    public ResalePaymentStatusResponse continuePayment(String orderCode) {
        ResaleService transactionalService = self != null ? self : this;
        ResalePaymentStatusResponse response = transactionalService.validateContinuePayment(orderCode);

        try {
            PaymentLinkResponse paymentLink = createPaymentLinkOrThrow(orderCode);
            response.setRedirectUrl(paymentLink.getRedirectUrl());
            return response;
        } catch (Exception e) {
            throw new AppException(ErrorCode.PAYMENT_GATEWAY_ERROR, "Unable to initialize resale payment", e);
        }
    }

    @Transactional
    public ResalePaymentStatusResponse validateContinuePayment(String orderCode) {
        Order order = orderRepository.findByOrderCodeForUpdate(orderCode)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Order not found"));
        ensureResaleOrder(order);

        ResaleListing listing = resaleListingRepository.findByPaymentOrder_IdForUpdate(order.getId())
                .orElseThrow(() -> new AppException(ErrorCode.BAD_REQUEST, "Resale listing is not awaiting payment"));
        TicketAsset asset = ticketAssetRepository.findByIdForUpdate(listing.getTicketAsset().getId())
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Ticket not found"));

        Long currentUserId = jwtUtil.getDataFromAuth().userId();
        boolean isBuyer = currentUserId.equals(order.getUserId())
                || (listing.getBuyerId() != null && currentUserId.equals(listing.getBuyerId()));
        if (!isBuyer) {
            throw new AppException(ErrorCode.FORBIDDEN, "You cannot continue this resale payment");
        }

        if (order.getOrderStatus() != OrderStatus.PENDING) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Resale order is not pending");
        }

        if (listing.getStatus() != ResaleListingStatus.PAYMENT_PENDING) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Resale listing is not awaiting payment");
        }

        if (listing.getPaymentOrder() == null || !order.getId().equals(listing.getPaymentOrder().getId())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Resale listing payment order does not match");
        }

        if (listing.getBuyerId() == null || !listing.getBuyerId().equals(order.getUserId())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Resale listing buyer does not match order buyer");
        }

        if (!listing.getSellerId().equals(asset.getCurrentOwnerId())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Ticket owner no longer matches listing seller");
        }

        return buildPaymentStatusResponse(order, listing, asset, null, true);
    }

    @Transactional
    public void expirePendingResaleOrder(Order order) {
        if (!isResaleOrder(order)) {
            return;
        }

        if (isNonPayableTerminalOrder(order.getOrderStatus())) {
            log.info("Resale order {} is already terminal as {}; skipping expiration",
                    order.getOrderCode(), order.getOrderStatus());
            return;
        }

        if (order.getOrderStatus() != OrderStatus.PENDING) {
            log.info("Resale order {} is not pending; skipping expiration", order.getOrderCode());
            return;
        }

        Optional<ResaleListing> listingOptional = resaleListingRepository.findByPaymentOrder_IdForUpdate(order.getId());
        if (listingOptional.isEmpty()) {
            log.warn("No resale listing found for pending resale order {}; marking order expired", order.getOrderCode());
            order.setOrderStatus(OrderStatus.EXPIRED);
            orderRepository.save(order);
            return;
        }

        ResaleListing listing = listingOptional.get();
        if (listing.getStatus() == ResaleListingStatus.SOLD) {
            log.info("Resale listing {} is sold; skipping expiration for order {}",
                    listing.getListingCode(), order.getOrderCode());
            return;
        }

        if (listing.getStatus() == ResaleListingStatus.PAYMENT_PENDING) {
            cancelPaymentIfPossible(order);
            restorePendingResaleCheckout(order, listing, OrderStatus.EXPIRED);
            return;
        }

        order.setOrderStatus(OrderStatus.EXPIRED);
        orderRepository.save(order);
    }

    @Transactional
    public void markResalePaymentFailed(Order order) {
        if (!isResaleOrder(order)) {
            return;
        }

        if (isNonPayableTerminalOrder(order.getOrderStatus())) {
            log.info("Resale order {} is already terminal as {}; skipping failure handling",
                    order.getOrderCode(), order.getOrderStatus());
            return;
        }

        if (order.getOrderStatus() != OrderStatus.PENDING) {
            log.info("Resale order {} is not pending; skipping failure handling", order.getOrderCode());
            return;
        }

        Optional<ResaleListing> listingOptional = resaleListingRepository.findByPaymentOrder_IdForUpdate(order.getId());
        if (listingOptional.isEmpty()) {
            log.warn("No resale listing found for failed resale order {}; marking order failed", order.getOrderCode());
            order.setOrderStatus(OrderStatus.PAYMENT_FAILED);
            orderRepository.save(order);
            return;
        }

        ResaleListing listing = listingOptional.get();
        if (listing.getStatus() == ResaleListingStatus.SOLD) {
            log.info("Resale listing {} is sold; skipping failure handling for order {}",
                    listing.getListingCode(), order.getOrderCode());
            return;
        }

        if (listing.getStatus() == ResaleListingStatus.PAYMENT_PENDING) {
            restorePendingResaleCheckout(order, listing, OrderStatus.PAYMENT_FAILED);
            return;
        }

        order.setOrderStatus(OrderStatus.PAYMENT_FAILED);
        orderRepository.save(order);
    }

    @Transactional
    public void cancelPendingResaleOrder(Order order) {
        if (!isResaleOrder(order)) {
            return;
        }

        if (isNonPayableTerminalOrder(order.getOrderStatus())) {
            log.info("Resale order {} is already terminal as {}; skipping cancellation",
                    order.getOrderCode(), order.getOrderStatus());
            return;
        }

        if (order.getOrderStatus() != OrderStatus.PENDING) {
            log.info("Resale order {} is not pending; skipping cancellation", order.getOrderCode());
            return;
        }

        Optional<ResaleListing> listingOptional = resaleListingRepository.findByPaymentOrder_IdForUpdate(order.getId());
        if (listingOptional.isEmpty()) {
            log.warn("No resale listing found for resale order {}; cancelling order only", order.getOrderCode());
            cancelPaymentIfPossible(order);
            order.setOrderStatus(OrderStatus.CANCELLED);
            orderRepository.save(order);
            return;
        }

        ResaleListing listing = listingOptional.get();
        if (listing.getStatus() == ResaleListingStatus.SOLD) {
            log.info("Resale listing {} is sold; skipping cancellation for order {}",
                    listing.getListingCode(), order.getOrderCode());
            return;
        }

        cancelPaymentIfPossible(order);

        if (listing.getStatus() == ResaleListingStatus.PAYMENT_PENDING) {
            restorePendingResaleCheckout(order, listing, OrderStatus.CANCELLED);
            return;
        }

        order.setOrderStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);
    }

    @Transactional
    public void finalizePaidResaleOrder(Order order, PaymentSuccessEvent event) {
        if (order.getOrderType() != OrderType.RESALE) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Order is not a resale order");
        }

        if (isNonPayableTerminalOrder(order.getOrderStatus())) {
            log.warn("Ignoring late resale payment-success for terminal order {} with status {}",
                    order.getOrderCode(), order.getOrderStatus());
            return;
        }

        ResaleListing listing = resaleListingRepository.findByPaymentOrder_IdForUpdate(order.getId())
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Resale listing not found"));
        TicketAsset asset = ticketAssetRepository.findByIdForUpdate(listing.getTicketAsset().getId())
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Ticket not found"));

        Long buyerId = listing.getBuyerId();
        Long sellerId = listing.getSellerId();

        if (order.getOrderStatus() == OrderStatus.CONFIRMED && listing.getStatus() == ResaleListingStatus.SOLD) {
            Long finalizedBuyerId = buyerId != null ? buyerId : order.getUserId();
            if (finalizedBuyerId != null && !finalizedBuyerId.equals(asset.getCurrentOwnerId())) {
                asset.setCurrentOwnerId(finalizedBuyerId);
                ticketAssetRepository.save(asset);
            }
            return;
        }

        validatePaidResaleState(order, listing, asset, sellerId, buyerId);

        Integer oldQrVersion = asset.getQrSecretVersion();
        Integer newQrVersion = oldQrVersion == null ? 1 : oldQrVersion + 1;

        asset.setCurrentOwnerId(buyerId);
        asset.setAccessStatus(TicketAccessStatus.VALID);
        asset.setCurrentResaleListingId(null);
        asset.setQrSecretVersion(newQrVersion);
        asset.setQrSecretHash(generateQrSecretHash(asset, listing, order, event, newQrVersion));

        listing.setStatus(ResaleListingStatus.SOLD);
        listing.setSoldAt(LocalDateTime.now());

        order.setOrderStatus(OrderStatus.CONFIRMED);

        ticketAssetRepository.save(asset);
        resaleListingRepository.save(listing);
        orderRepository.save(order);

        ticketProvenanceService.recordResalePurchased(asset, listing, order);
        ticketProvenanceService.recordOwnershipTransferred(asset, listing, order, sellerId, buyerId);
        ticketProvenanceService.recordQrRotated(asset, listing, order, sellerId, buyerId, oldQrVersion, newQrVersion);
    }

    private PaymentLinkResponse createPaymentLinkOrThrow(String orderCode) {
        BaseResponse<PaymentLinkResponse> response = paymentFeignClient.createPaymentLink(orderCode);
        PaymentLinkResponse paymentLink = response != null ? response.getData() : null;
        if (paymentLink == null
                || paymentLink.getRedirectUrl() == null
                || paymentLink.getRedirectUrl().isBlank()) {
            throw new AppException(ErrorCode.PAYMENT_GATEWAY_ERROR, "Unable to initialize resale payment");
        }
        return paymentLink;
    }

    private void ensureResaleOrder(Order order) {
        OrderType orderType = order.getOrderType() != null ? order.getOrderType() : OrderType.PRIMARY;
        if (orderType != OrderType.RESALE) {
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Resale order not found");
        }
    }

    private ResaleListing findListingForStatus(Order order) {
        if (order.getId() != null) {
            Optional<ResaleListing> paymentListing = resaleListingRepository.findByPaymentOrder_Id(order.getId());
            if (paymentListing.isPresent()) {
                return paymentListing.get();
            }
        }

        TicketAsset asset = findTicketAssetForOrder(order, null);
        if (asset == null || asset.getCurrentResaleListingId() == null) {
            return null;
        }

        return resaleListingRepository.findById(asset.getCurrentResaleListingId()).orElse(null);
    }

    private TicketAsset findTicketAssetForOrder(Order order, ResaleListing listing) {
        if (listing != null && listing.getTicketAsset() != null) {
            return listing.getTicketAsset();
        }

        if (order.getOrderItems() == null || order.getOrderItems().isEmpty()) {
            return null;
        }

        String ticketCode = order.getOrderItems().getFirst().getTicketCode();
        if (ticketCode == null || ticketCode.isBlank()) {
            return null;
        }

        return ticketAssetRepository.findByTicketCodeOrAssetCode(ticketCode, ticketCode).orElse(null);
    }

    private void validateCanViewResalePayment(Order order, ResaleListing listing, TicketAsset asset) {
        Long currentUserId = jwtUtil.getDataFromAuth().userId();
        boolean isBuyer = currentUserId.equals(order.getUserId())
                || (listing != null && listing.getBuyerId() != null && currentUserId.equals(listing.getBuyerId()));
        boolean isSeller = listing != null && currentUserId.equals(listing.getSellerId());
        boolean isCurrentOwner = asset != null && currentUserId.equals(asset.getCurrentOwnerId());

        if (!isBuyer && !isSeller && !isCurrentOwner) {
            throw new AppException(ErrorCode.FORBIDDEN, "You cannot view this resale payment");
        }
    }

    private boolean paymentTransactionExists(String orderCode) {
        try {
            BaseResponse<PaymentTransactionResponse> response = paymentFeignClient.getPaymentInfo(orderCode);
            return response != null && response.getData() != null;
        } catch (Exception e) {
            log.info("Payment transaction not available for resale order {}", orderCode);
            return false;
        }
    }

    private ResalePaymentStatusResponse buildPaymentStatusResponse(
            Order order,
            ResaleListing listing,
            TicketAsset asset,
            String redirectUrl,
            boolean hasPaymentTransaction
    ) {
        ResalePaymentResultStatus resultStatus = determinePaymentResultStatus(order, listing, hasPaymentTransaction);
        boolean canContinuePayment = resultStatus == ResalePaymentResultStatus.PENDING
                && order.getOrderStatus() == OrderStatus.PENDING
                && listing != null
                && listing.getStatus() == ResaleListingStatus.PAYMENT_PENDING;
        boolean canCheckoutAgain = listing != null
                && listing.getStatus() == ResaleListingStatus.ACTIVE
                && (resultStatus == ResalePaymentResultStatus.CANCELLED
                || resultStatus == ResalePaymentResultStatus.EXPIRED
                || resultStatus == ResalePaymentResultStatus.FAILED
                || resultStatus == ResalePaymentResultStatus.GATEWAY_ERROR);

        return ResalePaymentStatusResponse.builder()
                .orderCode(order.getOrderCode())
                .listingCode(listing != null ? listing.getListingCode() : null)
                .ticketAssetId(asset != null ? asset.getId() : null)
                .paymentMethod(order.getPaymentMethod())
                .orderStatus(order.getOrderStatus())
                .listingStatus(listing != null ? listing.getStatus() : null)
                .resultStatus(resultStatus)
                .amount(order.getFinalAmount())
                .buyerId(listing != null && listing.getBuyerId() != null ? listing.getBuyerId() : order.getUserId())
                .sellerId(listing != null ? listing.getSellerId() : asset != null ? asset.getCurrentOwnerId() : null)
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .expiresAt(listing != null ? listing.getExpiresAt() : null)
                .canContinuePayment(canContinuePayment)
                .canCheckoutAgain(canCheckoutAgain)
                .canChooseAnotherMethod(canCheckoutAgain)
                .redirectUrl(redirectUrl)
                .message(messageForResultStatus(resultStatus))
                .build();
    }

    private ResalePaymentResultStatus determinePaymentResultStatus(
            Order order,
            ResaleListing listing,
            boolean hasPaymentTransaction
    ) {
        ResaleListingStatus listingStatus = listing != null ? listing.getStatus() : null;
        if (order.getOrderStatus() == OrderStatus.CONFIRMED && listingStatus == ResaleListingStatus.SOLD) {
            return ResalePaymentResultStatus.SUCCESS;
        }
        if (order.getOrderStatus() == OrderStatus.PENDING && listingStatus == ResaleListingStatus.PAYMENT_PENDING) {
            return ResalePaymentResultStatus.PENDING;
        }
        if (order.getOrderStatus() == OrderStatus.EXPIRED) {
            return ResalePaymentResultStatus.EXPIRED;
        }
        if (order.getOrderStatus() == OrderStatus.CANCELLED) {
            return ResalePaymentResultStatus.CANCELLED;
        }
        if (order.getOrderStatus() == OrderStatus.PAYMENT_FAILED) {
            return hasPaymentTransaction ? ResalePaymentResultStatus.FAILED : ResalePaymentResultStatus.GATEWAY_ERROR;
        }
        return ResalePaymentResultStatus.UNKNOWN;
    }

    private String messageForResultStatus(ResalePaymentResultStatus resultStatus) {
        return switch (resultStatus) {
            case SUCCESS -> "Resale payment completed";
            case PENDING -> "Resale payment is pending";
            case CANCELLED -> "Resale payment was cancelled";
            case EXPIRED -> "Resale payment expired";
            case FAILED -> "Resale payment failed";
            case GATEWAY_ERROR -> "Unable to initialize resale payment";
            case UNKNOWN -> "Unable to determine resale payment status";
        };
    }

    private void restorePendingResaleCheckout(
            Order order,
            ResaleListing listing,
            OrderStatus restoredOrderStatus
    ) {
        order.setOrderStatus(restoredOrderStatus);

        listing.setStatus(ResaleListingStatus.ACTIVE);
        listing.setBuyerId(null);
        listing.setPaymentOrder(null);

        TicketAsset listingAsset = listing.getTicketAsset();
        Long ticketAssetId = listingAsset != null ? listingAsset.getId() : null;
        if (ticketAssetId != null) {
            Optional<TicketAsset> assetOptional = ticketAssetRepository.findByIdForUpdate(ticketAssetId);
            if (assetOptional.isPresent()) {
                TicketAsset asset = assetOptional.get();
                asset.setCurrentOwnerId(listing.getSellerId());
                asset.setAccessStatus(TicketAccessStatus.LOCKED_RESALE);
                asset.setCurrentResaleListingId(listing.getId());
                ticketAssetRepository.save(asset);
            } else {
                log.warn("Ticket asset {} not found while restoring resale listing {}", ticketAssetId, listing.getListingCode());
            }
        }

        resaleListingRepository.save(listing);
        orderRepository.save(order);
    }

    private boolean isResaleOrder(Order order) {
        return order != null && order.getOrderType() == OrderType.RESALE;
    }

    private boolean isNonPayableTerminalOrder(OrderStatus orderStatus) {
        return orderStatus == OrderStatus.EXPIRED
                || orderStatus == OrderStatus.CANCELLED
                || orderStatus == OrderStatus.PAYMENT_FAILED;
    }

    private void cancelPaymentIfPossible(Order order) {
        try {
            BaseResponse<Boolean> response = paymentFeignClient.cancelPayment(order.getOrderCode());
            if (response == null || !Boolean.TRUE.equals(response.getData())) {
                log.warn("Payment cancellation returned non-success for resale order {}", order.getOrderCode());
            }
        } catch (Exception e) {
            log.warn("Payment cancellation failed for resale order {}", order.getOrderCode(), e);
        }
    }

    private ResaleQuoteResponse toQuoteResponse(
            TicketAsset asset,
            ResalePricingService.ResalePricing pricing,
            ResaleDecision decision
    ) {
        return ResaleQuoteResponse.builder()
                .ticketAssetId(asset.getId())
                .originalPrice(pricing.originalPrice())
                .listingPrice(pricing.listingPrice())
                .priceCap(pricing.priceCap())
                .platformFeeRate(pricing.platformFeeRate())
                .organizerRoyaltyRate(pricing.organizerRoyaltyRate())
                .platformFeeAmount(pricing.platformFeeAmount())
                .organizerRoyaltyAmount(pricing.organizerRoyaltyAmount())
                .sellerPayoutAmount(pricing.sellerPayoutAmount())
                .valid(decision.valid())
                .reasonCode(decision.reasonCode())
                .reasonMessage(decision.reasonMessage())
                .build();
    }

    private ResaleDecision evaluateListingRequest(
            TicketAsset asset,
            BigDecimal requestedListingPrice,
            ResalePricingService.ResalePricing pricing,
            LocalDateTime now
    ) {
        if (asset.getAccessStatus() != TicketAccessStatus.VALID) {
            return new ResaleDecision(false, "INVALID_ACCESS_STATUS", "Ticket access status does not allow resale");
        }

        if (asset.getUsedAt() != null) {
            return new ResaleDecision(false, "ALREADY_USED", "Ticket has already been used");
        }

        if (asset.getCurrentResaleListingId() != null) {
            return new ResaleDecision(false, "ALREADY_ON_SALE", "Ticket is already on sale");
        }

        if (asset.getEventEndTime() != null && asset.getEventEndTime().isBefore(now)) {
            return new ResaleDecision(false, "EVENT_ENDED", "Event has already ended");
        }

        if (requestedListingPrice == null || requestedListingPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return new ResaleDecision(false, "INVALID_PRICE", "Listing price must be positive");
        }

        if (pricing.listingPrice().compareTo(pricing.priceCap()) > 0) {
            return new ResaleDecision(false, "PRICE_EXCEEDS_CAP", "Listing price exceeds resale price cap");
        }

        return new ResaleDecision(true, "ELIGIBLE", "Ticket is eligible for resale");
    }

    private void validateListingStillCheckoutable(ResaleListing listing, TicketAsset asset) {
        if (!listing.getSellerId().equals(asset.getCurrentOwnerId())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Ticket owner no longer matches listing seller");
        }

        if (asset.getAccessStatus() != TicketAccessStatus.LOCKED_RESALE) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Ticket is not locked for resale");
        }

        if (!listing.getId().equals(asset.getCurrentResaleListingId())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Ticket is not linked to this resale listing");
        }

        if (asset.getEventEndTime() != null && asset.getEventEndTime().isBefore(LocalDateTime.now())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Event has already ended");
        }
    }

    private void validatePaidResaleState(
            Order order,
            ResaleListing listing,
            TicketAsset asset,
            Long sellerId,
            Long buyerId
    ) {
        if (order.getOrderStatus() != OrderStatus.PENDING) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Resale order is not pending");
        }

        if (listing.getStatus() != ResaleListingStatus.PAYMENT_PENDING) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Resale listing is not awaiting payment");
        }

        if (listing.getPaymentOrder() == null || !order.getId().equals(listing.getPaymentOrder().getId())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Resale listing payment order does not match");
        }

        if (buyerId == null || !buyerId.equals(order.getUserId())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Resale listing buyer does not match order buyer");
        }

        if (!sellerId.equals(asset.getCurrentOwnerId())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Ticket owner no longer matches listing seller");
        }

        if (asset.getAccessStatus() != TicketAccessStatus.LOCKED_RESALE) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Ticket is not locked for resale");
        }

        if (asset.getCurrentResaleListingId() != null && !listing.getId().equals(asset.getCurrentResaleListingId())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Ticket is not linked to this resale listing");
        }
    }

    private Order buildResaleOrder(
            ResaleCheckoutRequest request,
            Long buyerId,
            ResaleListing listing,
            TicketAsset asset
    ) {
        Order order = Order.builder()
                .orderCode(generateNumericOrderCode())
                .userId(buyerId)
                .fullName(request.getFullName())
                .phoneNumber(request.getPhoneNumber())
                .email(request.getEmail())
                .paymentMethod(request.getPaymentMethod())
                .totalAmount(listing.getListingPrice())
                .discountAmount(BigDecimal.ZERO)
                .finalAmount(listing.getListingPrice())
                .orderStatus(OrderStatus.PENDING)
                .orderType(OrderType.RESALE)
                .eventId(asset.getEventId())
                .bookingSessionId(null)
                .build();

        OrderItem item = OrderItem.builder()
                .order(order)
                .ticketTypeId(asset.getTicketTypeId())
                .ticketTypeName(asset.getTicketTypeName())
                .unitPrice(listing.getListingPrice())
                .ticketCode(asset.getTicketCode() != null ? asset.getTicketCode() : asset.getAssetCode())
                .tokenId(asset.getTokenId())
                .build();

        order.addOrderItem(item);
        return order;
    }

    private String generateListingCode() {
        return "RSL-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private String generateNumericOrderCode() {
        String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("ddMMyy"));
        for (int i = 0; i < 10; i++) {
            int randomPart = ThreadLocalRandom.current().nextInt(100000, 1_000_000);
            String orderCode = datePart + randomPart;
            if (orderRepository.findByOrderCode(orderCode).isEmpty()) {
                return orderCode;
            }
        }
        throw new AppException(ErrorCode.CONFLICT, "Could not generate unique resale order code");
    }

    private String generateQrSecretHash(
            TicketAsset asset,
            ResaleListing listing,
            Order order,
            PaymentSuccessEvent event,
            Integer newQrVersion
    ) {
        try {
            String material = asset.getId()
                    + ":" + listing.getListingCode()
                    + ":" + order.getOrderCode()
                    + ":" + event.getTransactionId()
                    + ":" + newQrVersion
                    + ":" + UUID.randomUUID();
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(material.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "Failed to rotate QR secret hash", e);
        }
    }

    private record ResaleDecision(boolean valid, String reasonCode, String reasonMessage) {
    }
}
