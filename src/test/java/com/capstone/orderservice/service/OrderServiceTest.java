package com.capstone.orderservice.service;

import com.capstone.orderservice.client.InventoryFeignClient;
import com.capstone.orderservice.client.PaymentFeignClient;
import com.capstone.orderservice.dto.event.OrderPaidEvent;
import com.capstone.orderservice.dto.event.PaymentSuccessEvent;
import com.capstone.orderservice.entity.Order;
import com.capstone.orderservice.entity.OrderItem;
import com.capstone.orderservice.enums.OrderStatus;
import com.capstone.orderservice.enums.OrderType;
import com.capstone.orderservice.producer.RedisStreamProducer;
import com.capstone.orderservice.repository.OrderRepository;
import com.capstone.orderservice.security.JwtUtil;
import com.capstone.orderservice.util.OrderUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {
    @Mock
    private OrderRepository orderRepository;

    @Mock
    private VoucherService voucherService;

    @Mock
    private OrderUtil orderUtil;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private InventoryFeignClient inventoryFeignClient;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private RedisStreamProducer redisStreamProducer;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private PaymentFeignClient paymentFeignClient;

    @Mock
    private TicketAssetService ticketAssetService;

    @Mock
    private ResaleService resaleService;

    @InjectMocks
    private OrderService orderService;

    @Test
    void commitTicketPrimaryPublishesOrderPaid() {
        Order order = primaryOrder(null);
        order.addOrderItem(orderItem(300L));
        order.addOrderItem(orderItem(300L));
        when(orderUtil.getOrderByOrderCode("300426123456")).thenReturn(order);

        PaymentSuccessEvent event = paymentSuccessEvent();
        orderService.commitTicket(event);

        ArgumentCaptor<OrderPaidEvent> eventCaptor = ArgumentCaptor.forClass(OrderPaidEvent.class);
        verify(redisStreamProducer).sendMessage(eq("order-paid"), eventCaptor.capture());
        assertThat(eventCaptor.getValue().getOrderCode()).isEqualTo("300426123456");
        assertThat(eventCaptor.getValue().getItems()).hasSize(1);
        assertThat(eventCaptor.getValue().getItems().getFirst().getTicketTypeId()).isEqualTo(300L);
        assertThat(eventCaptor.getValue().getItems().getFirst().getQuantity()).isEqualTo(2);
        verifyNoInteractions(resaleService);
    }

    @Test
    void commitTicketResaleFinalizesWithoutPublishingOrderPaid() {
        Order order = primaryOrder(OrderType.RESALE);
        when(orderUtil.getOrderByOrderCode("300426123456")).thenReturn(order);

        PaymentSuccessEvent event = paymentSuccessEvent();
        orderService.commitTicket(event);

        verify(resaleService).finalizePaidResaleOrder(order, event);
        verify(redisStreamProducer, never()).sendMessage(eq("order-paid"), any());
    }

    @Test
    void markFailedResaleDelegatesWithoutInventoryRelease() {
        Order order = primaryOrder(OrderType.RESALE);
        when(orderRepository.findByOrderCode("300426123456")).thenReturn(Optional.of(order));

        orderService.markFailed("300426123456");

        verify(resaleService).markResalePaymentFailed(order);
        verify(inventoryFeignClient, never()).releaseTickets(any());
    }

    @Test
    void cancelOrderResaleDelegatesWithoutInventoryOrPrimaryPaymentCancel() {
        Order order = primaryOrder(OrderType.RESALE);
        when(orderUtil.getOrderByOrderCode("300426123456")).thenReturn(order);

        orderService.cancelOrder("300426123456");

        verify(resaleService).cancelPendingResaleOrder(order);
        verify(inventoryFeignClient, never()).releaseTickets(any());
        verify(paymentFeignClient, never()).cancelPayment(any());
    }

    private Order primaryOrder(OrderType orderType) {
        return Order.builder()
                .id(500L)
                .orderCode("300426123456")
                .userId(20L)
                .orderType(orderType)
                .orderStatus(OrderStatus.PENDING)
                .build();
    }

    private OrderItem orderItem(Long ticketTypeId) {
        return OrderItem.builder()
                .ticketTypeId(ticketTypeId)
                .unitPrice(new BigDecimal("100000.00"))
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
