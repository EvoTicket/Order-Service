package com.capstone.orderservice.service;

import com.capstone.orderservice.client.PaymentFeignClient;
import com.capstone.orderservice.dto.BaseResponse;
import com.capstone.orderservice.dto.request.CreateResaleListingRequest;
import com.capstone.orderservice.dto.request.ResaleCheckoutRequest;
import com.capstone.orderservice.dto.request.ResaleQuoteRequest;
import com.capstone.orderservice.dto.event.PaymentSuccessEvent;
import com.capstone.orderservice.dto.response.ResaleCheckoutResponse;
import com.capstone.orderservice.dto.response.ResaleListingResponse;
import com.capstone.orderservice.dto.response.ResaleQuoteResponse;
import com.capstone.orderservice.entity.Order;
import com.capstone.orderservice.entity.ResaleListing;
import com.capstone.orderservice.entity.TicketAsset;
import com.capstone.orderservice.enums.OrderStatus;
import com.capstone.orderservice.enums.OrderType;
import com.capstone.orderservice.enums.PaymentMethod;
import com.capstone.orderservice.enums.ResaleListingStatus;
import com.capstone.orderservice.enums.TicketAccessStatus;
import com.capstone.orderservice.enums.TicketChainStatus;
import com.capstone.orderservice.repository.OrderRepository;
import com.capstone.orderservice.repository.ResaleListingRepository;
import com.capstone.orderservice.repository.TicketAssetRepository;
import com.capstone.orderservice.security.JwtUtil;
import com.capstone.orderservice.security.TokenMetaData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ResaleServiceTest {
    @Mock
    private TicketAssetRepository ticketAssetRepository;

    @Mock
    private ResaleListingRepository resaleListingRepository;

    @Spy
    private ResalePricingService resalePricingService = new ResalePricingService();

    @Mock
    private TicketProvenanceService ticketProvenanceService;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private PaymentFeignClient paymentFeignClient;

    @InjectMocks
    private ResaleService resaleService;

    @Test
    void quoteReturnsEligiblePricingForOwnedTicket() {
        when(jwtUtil.getDataFromAuth()).thenReturn(new TokenMetaData(10L, false, null));
        when(ticketAssetRepository.findByIdAndCurrentOwnerId(1L, 10L)).thenReturn(Optional.of(validAsset()));

        ResaleQuoteResponse response = resaleService.quote(ResaleQuoteRequest.builder()
                .ticketAssetId(1L)
                .listingPrice(new BigDecimal("105000.00"))
                .build());

        assertThat(response.getValid()).isTrue();
        assertThat(response.getReasonCode()).isEqualTo("ELIGIBLE");
        assertThat(response.getPriceCap()).isEqualByComparingTo("110000.00");
        assertThat(response.getPlatformFeeAmount()).isEqualByComparingTo("2100.00");
        assertThat(response.getSellerPayoutAmount()).isEqualByComparingTo("102900.00");
    }

    @Test
    void createListingLocksTicketAndRecordsProvenance() {
        when(jwtUtil.getDataFromAuth()).thenReturn(new TokenMetaData(10L, false, null));
        TicketAsset asset = validAsset();
        when(ticketAssetRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(asset));
        when(resaleListingRepository.existsByTicketAsset_IdAndStatusIn(any(), any())).thenReturn(false);
        when(resaleListingRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            ResaleListing listing = invocation.getArgument(0);
            listing.setId(77L);
            return listing;
        });

        ResaleListingResponse response = resaleService.createListing(CreateResaleListingRequest.builder()
                .ticketAssetId(1L)
                .listingPrice(new BigDecimal("105000.00"))
                .build());

        assertThat(response.getListingId()).isEqualTo(77L);
        assertThat(response.getStatus()).isEqualTo(ResaleListingStatus.ACTIVE);
        assertThat(asset.getAccessStatus()).isEqualTo(TicketAccessStatus.LOCKED_RESALE);
        assertThat(asset.getCurrentResaleListingId()).isEqualTo(77L);
        verify(ticketProvenanceService).recordResaleListed(eq(asset), any(ResaleListing.class));
    }

    @Test
    void cancelListingRestoresTicketAndRecordsProvenance() {
        when(jwtUtil.getDataFromAuth()).thenReturn(new TokenMetaData(10L, false, null));
        TicketAsset asset = validAsset();
        asset.setAccessStatus(TicketAccessStatus.LOCKED_RESALE);
        asset.setCurrentResaleListingId(77L);

        ResaleListing listing = activeListing(asset);
        when(resaleListingRepository.findByListingCodeForUpdate("RSL-TEST")).thenReturn(Optional.of(listing));
        when(ticketAssetRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(asset));
        when(resaleListingRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ResaleListingResponse response = resaleService.cancelListing("RSL-TEST");

        assertThat(response.getStatus()).isEqualTo(ResaleListingStatus.CANCELLED);
        assertThat(asset.getAccessStatus()).isEqualTo(TicketAccessStatus.VALID);
        assertThat(asset.getCurrentResaleListingId()).isNull();
        assertThat(listing.getCancelledAt()).isNotNull();
        verify(ticketProvenanceService).recordResaleCancelled(asset, listing);
    }

    @Test
    void createListingPersistsExpectedPriceValues() {
        when(jwtUtil.getDataFromAuth()).thenReturn(new TokenMetaData(10L, false, null));
        TicketAsset asset = validAsset();
        when(ticketAssetRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(asset));
        when(resaleListingRepository.existsByTicketAsset_IdAndStatusIn(any(), any())).thenReturn(false);
        when(resaleListingRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            ResaleListing listing = invocation.getArgument(0);
            listing.setId(77L);
            return listing;
        });

        resaleService.createListing(CreateResaleListingRequest.builder()
                .ticketAssetId(1L)
                .listingPrice(new BigDecimal("105000.00"))
                .build());

        ArgumentCaptor<ResaleListing> listingCaptor = ArgumentCaptor.forClass(ResaleListing.class);
        verify(resaleListingRepository).saveAndFlush(listingCaptor.capture());
        ResaleListing saved = listingCaptor.getValue();
        assertThat(saved.getOriginalPrice()).isEqualByComparingTo("100000.00");
        assertThat(saved.getListingPrice()).isEqualByComparingTo("105000.00");
        assertThat(saved.getPriceCap()).isEqualByComparingTo("110000.00");
        assertThat(saved.getPlatformFeeAmount()).isEqualByComparingTo("2100.00");
        assertThat(saved.getSellerPayoutAmount()).isEqualByComparingTo("102900.00");
    }

    @Test
    void getActiveListingsReturnsOnlyRepositoryActivePage() {
        TicketAsset asset = validAsset();
        ResaleListing listing = activeListing(asset);
        when(resaleListingRepository.findActiveListings(
                eq(ResaleListingStatus.ACTIVE),
                eq(200L),
                eq(300L),
                any(),
                any(),
                any()
        )).thenReturn(new PageImpl<>(List.of(listing)));

        Page<ResaleListingResponse> response = resaleService.getActiveListings(
                200L,
                300L,
                new BigDecimal("100000.00"),
                new BigDecimal("110000.00"),
                PageRequest.of(0, 20)
        );

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().getFirst().getListingCode()).isEqualTo("RSL-TEST");
    }

    @Test
    void getActiveListingDetailRejectsNonActiveListing() {
        ResaleListing listing = activeListing(validAsset());
        listing.setStatus(ResaleListingStatus.PAYMENT_PENDING);
        when(resaleListingRepository.findByListingCode("RSL-TEST")).thenReturn(Optional.of(listing));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> resaleService.getActiveListingDetail("RSL-TEST"))
                .hasMessageContaining("Resale listing not found");
    }

    @Test
    void checkoutCreatesResaleOrderAndMovesListingToPaymentPending() {
        when(jwtUtil.getDataFromAuth()).thenReturn(new TokenMetaData(20L, false, null));
        TicketAsset asset = validAsset();
        asset.setAccessStatus(TicketAccessStatus.LOCKED_RESALE);
        asset.setCurrentResaleListingId(77L);

        ResaleListing listing = activeListing(asset);
        when(resaleListingRepository.findByListingCodeForUpdate("RSL-TEST")).thenReturn(Optional.of(listing));
        when(ticketAssetRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(asset));
        when(orderRepository.findByOrderCode(any())).thenReturn(Optional.empty());
        when(orderRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId(500L);
            return order;
        });
        when(resaleListingRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ResaleCheckoutResponse response = resaleService.checkout("RSL-TEST", ResaleCheckoutRequest.builder()
                .paymentMethod(PaymentMethod.PAYOS)
                .fullName("Buyer")
                .email("buyer@example.com")
                .phoneNumber("0900000000")
                .build());

        assertThat(response.getOrderId()).isEqualTo(500L);
        assertThat(response.getOrderType()).isEqualTo(OrderType.RESALE);
        assertThat(response.getOrderStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(response.getStatus()).isEqualTo(ResaleListingStatus.PAYMENT_PENDING);
        assertThat(listing.getBuyerId()).isEqualTo(20L);
        assertThat(listing.getPaymentOrder()).isNotNull();
        assertThat(asset.getCurrentOwnerId()).isEqualTo(10L);
        assertThat(asset.getAccessStatus()).isEqualTo(TicketAccessStatus.LOCKED_RESALE);

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).saveAndFlush(orderCaptor.capture());
        Order savedOrder = orderCaptor.getValue();
        assertThat(savedOrder.getOrderItems()).hasSize(1);
        assertThat(savedOrder.getOrderItems().getFirst().getUnitPrice()).isEqualByComparingTo("105000.00");
    }

    @Test
    void finalizePaidResaleOrderTransfersOwnershipAndMarksSold() {
        Order order = resaleOrder();
        TicketAsset asset = validAsset();
        asset.setAccessStatus(TicketAccessStatus.LOCKED_RESALE);
        asset.setCurrentResaleListingId(77L);
        asset.setQrSecretVersion(1);
        asset.setQrSecretHash("old-hash");

        ResaleListing listing = paymentPendingListing(asset, order);
        when(resaleListingRepository.findByPaymentOrder_IdForUpdate(500L)).thenReturn(Optional.of(listing));
        when(ticketAssetRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(asset));

        resaleService.finalizePaidResaleOrder(order, paymentSuccessEvent());

        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(listing.getStatus()).isEqualTo(ResaleListingStatus.SOLD);
        assertThat(listing.getSoldAt()).isNotNull();
        assertThat(asset.getCurrentOwnerId()).isEqualTo(20L);
        assertThat(asset.getAccessStatus()).isEqualTo(TicketAccessStatus.VALID);
        assertThat(asset.getCurrentResaleListingId()).isNull();
        assertThat(asset.getQrSecretVersion()).isEqualTo(2);
        assertThat(asset.getQrSecretHash()).isNotEqualTo("old-hash");
        verify(ticketProvenanceService).recordResalePurchased(asset, listing, order);
        verify(ticketProvenanceService).recordOwnershipTransferred(asset, listing, order, 10L, 20L);
        verify(ticketProvenanceService).recordQrRotated(asset, listing, order, 10L, 20L, 1, 2);
    }

    @Test
    void finalizePaidResaleOrderAlreadyFinalizedReturnsSafely() {
        Order order = resaleOrder();
        order.setOrderStatus(OrderStatus.CONFIRMED);
        TicketAsset asset = validAsset();
        asset.setCurrentOwnerId(20L);
        asset.setAccessStatus(TicketAccessStatus.VALID);
        asset.setCurrentResaleListingId(null);

        ResaleListing listing = paymentPendingListing(asset, order);
        listing.setStatus(ResaleListingStatus.SOLD);
        when(resaleListingRepository.findByPaymentOrder_IdForUpdate(500L)).thenReturn(Optional.of(listing));
        when(ticketAssetRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(asset));

        resaleService.finalizePaidResaleOrder(order, paymentSuccessEvent());

        verifyNoInteractions(ticketProvenanceService);
        verify(resaleListingRepository, never()).save(any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void expirePendingResaleOrderRestoresListingActiveAndKeepsTicketLocked() {
        Order order = resaleOrder();
        TicketAsset asset = validAsset();
        asset.setAccessStatus(TicketAccessStatus.LOCKED_RESALE);
        asset.setCurrentResaleListingId(77L);
        ResaleListing listing = paymentPendingListing(asset, order);

        when(resaleListingRepository.findByPaymentOrder_IdForUpdate(500L)).thenReturn(Optional.of(listing));
        when(ticketAssetRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(asset));
        when(paymentFeignClient.cancelPayment("300426123456")).thenReturn(BaseResponse.ok(true));
        when(resaleListingRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        resaleService.expirePendingResaleOrder(order);

        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.EXPIRED);
        assertThat(listing.getStatus()).isEqualTo(ResaleListingStatus.ACTIVE);
        assertThat(listing.getBuyerId()).isNull();
        assertThat(listing.getPaymentOrder()).isNull();
        assertThat(asset.getCurrentOwnerId()).isEqualTo(10L);
        assertThat(asset.getAccessStatus()).isEqualTo(TicketAccessStatus.LOCKED_RESALE);
        assertThat(asset.getCurrentResaleListingId()).isEqualTo(77L);
        verify(paymentFeignClient).cancelPayment("300426123456");
    }

    @Test
    void markResalePaymentFailedRestoresListingActiveWithoutPaymentCancel() {
        Order order = resaleOrder();
        TicketAsset asset = validAsset();
        asset.setAccessStatus(TicketAccessStatus.LOCKED_RESALE);
        asset.setCurrentResaleListingId(77L);
        ResaleListing listing = paymentPendingListing(asset, order);

        when(resaleListingRepository.findByPaymentOrder_IdForUpdate(500L)).thenReturn(Optional.of(listing));
        when(ticketAssetRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(asset));
        when(resaleListingRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        resaleService.markResalePaymentFailed(order);

        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED);
        assertThat(listing.getStatus()).isEqualTo(ResaleListingStatus.ACTIVE);
        assertThat(listing.getBuyerId()).isNull();
        assertThat(listing.getPaymentOrder()).isNull();
        assertThat(asset.getAccessStatus()).isEqualTo(TicketAccessStatus.LOCKED_RESALE);
        verifyNoInteractions(paymentFeignClient);
    }

    @Test
    void cancelPendingResaleOrderRestoresListingActiveAndCancelsPayment() {
        Order order = resaleOrder();
        TicketAsset asset = validAsset();
        asset.setAccessStatus(TicketAccessStatus.LOCKED_RESALE);
        asset.setCurrentResaleListingId(77L);
        ResaleListing listing = paymentPendingListing(asset, order);

        when(resaleListingRepository.findByPaymentOrder_IdForUpdate(500L)).thenReturn(Optional.of(listing));
        when(ticketAssetRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(asset));
        when(paymentFeignClient.cancelPayment("300426123456")).thenReturn(BaseResponse.ok(true));
        when(resaleListingRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        resaleService.cancelPendingResaleOrder(order);

        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(listing.getStatus()).isEqualTo(ResaleListingStatus.ACTIVE);
        assertThat(listing.getBuyerId()).isNull();
        assertThat(listing.getPaymentOrder()).isNull();
        assertThat(asset.getCurrentOwnerId()).isEqualTo(10L);
        assertThat(asset.getAccessStatus()).isEqualTo(TicketAccessStatus.LOCKED_RESALE);
        verify(paymentFeignClient).cancelPayment("300426123456");
    }

    @Test
    void finalizePaidResaleOrderIgnoresExpiredOrderWithoutTransfer() {
        Order order = resaleOrder();
        order.setOrderStatus(OrderStatus.EXPIRED);

        resaleService.finalizePaidResaleOrder(order, paymentSuccessEvent());

        verify(resaleListingRepository, never()).findByPaymentOrder_IdForUpdate(any());
        verifyNoInteractions(ticketAssetRepository, ticketProvenanceService);
    }

    private TicketAsset validAsset() {
        return TicketAsset.builder()
                .id(1L)
                .assetCode("ASSET-1")
                .originalOrderId(100L)
                .originalOrderCode("300426123456")
                .eventId(200L)
                .eventName("Concert")
                .ticketTypeId(300L)
                .ticketTypeName("VIP")
                .originalPrice(new BigDecimal("100000.00"))
                .originalBuyerId(10L)
                .currentOwnerId(10L)
                .accessStatus(TicketAccessStatus.VALID)
                .chainStatus(TicketChainStatus.WEB2_ONLY)
                .eventEndTime(LocalDateTime.now().plusDays(1))
                .build();
    }

    private ResaleListing activeListing(TicketAsset asset) {
        return ResaleListing.builder()
                .id(77L)
                .listingCode("RSL-TEST")
                .ticketAsset(asset)
                .sellerId(10L)
                .originalPrice(new BigDecimal("100000.00"))
                .listingPrice(new BigDecimal("105000.00"))
                .priceCap(new BigDecimal("110000.00"))
                .platformFeeAmount(new BigDecimal("2100.00"))
                .organizerRoyaltyAmount(BigDecimal.ZERO)
                .sellerPayoutAmount(new BigDecimal("102900.00"))
                .status(ResaleListingStatus.ACTIVE)
                .build();
    }

    private ResaleListing paymentPendingListing(TicketAsset asset, Order order) {
        return ResaleListing.builder()
                .id(77L)
                .listingCode("RSL-TEST")
                .ticketAsset(asset)
                .sellerId(10L)
                .buyerId(20L)
                .paymentOrder(order)
                .originalPrice(new BigDecimal("100000.00"))
                .listingPrice(new BigDecimal("105000.00"))
                .priceCap(new BigDecimal("110000.00"))
                .platformFeeAmount(new BigDecimal("2100.00"))
                .organizerRoyaltyAmount(BigDecimal.ZERO)
                .sellerPayoutAmount(new BigDecimal("102900.00"))
                .status(ResaleListingStatus.PAYMENT_PENDING)
                .build();
    }

    private Order resaleOrder() {
        return Order.builder()
                .id(500L)
                .orderCode("300426123456")
                .userId(20L)
                .orderType(OrderType.RESALE)
                .orderStatus(OrderStatus.PENDING)
                .paymentMethod(PaymentMethod.PAYOS)
                .build();
    }

    private PaymentSuccessEvent paymentSuccessEvent() {
        return PaymentSuccessEvent.builder()
                .orderCode("300426123456")
                .transactionId("TX-1")
                .transactionDateTime("2026-04-30T10:00:00")
                .build();
    }
}
