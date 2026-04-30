package com.capstone.orderservice.scheduler;

import com.capstone.orderservice.client.InventoryFeignClient;
import com.capstone.orderservice.client.PaymentFeignClient;
import com.capstone.orderservice.entity.Order;
import com.capstone.orderservice.enums.OrderStatus;
import com.capstone.orderservice.enums.OrderType;
import com.capstone.orderservice.repository.OrderRepository;
import com.capstone.orderservice.service.ResaleService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderSchedulerTest {
    @Mock
    private OrderRepository orderRepository;

    @Mock
    private InventoryFeignClient inventoryFeignClient;

    @Mock
    private PaymentFeignClient paymentFeignClient;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ResaleService resaleService;

    @InjectMocks
    private OrderScheduler orderScheduler;

    @Test
    void releaseExpiredOrdersKeepsPrimarySessionExpirationBehavior() {
        Order order = order(OrderType.PRIMARY);
        order.setBookingSessionId("booking-1");
        when(orderRepository.findByOrderStatusAndCreatedAtBefore(eq(OrderStatus.PENDING), any(LocalDateTime.class)))
                .thenReturn(List.of(order));
        when(redisTemplate.hasKey("booking:session:booking-1")).thenReturn(false);

        orderScheduler.releaseExpiredOrders();

        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.EXPIRED);
        verify(paymentFeignClient).cancelPayment("300426123456");
        verify(orderRepository).save(order);
        verifyNoInteractions(resaleService);
    }

    @Test
    void releaseExpiredOrdersDelegatesResaleWithoutInventoryOrBookingSessionLookup() {
        Order order = order(OrderType.RESALE);
        when(orderRepository.findByOrderStatusAndCreatedAtBefore(eq(OrderStatus.PENDING), any(LocalDateTime.class)))
                .thenReturn(List.of(order));

        orderScheduler.releaseExpiredOrders();

        verify(resaleService).expirePendingResaleOrder(order);
        verify(redisTemplate, never()).hasKey(any());
        verify(paymentFeignClient, never()).cancelPayment(any());
        verifyNoInteractions(inventoryFeignClient);
    }

    private Order order(OrderType orderType) {
        return Order.builder()
                .id(500L)
                .orderCode("300426123456")
                .userId(20L)
                .orderType(orderType)
                .orderStatus(OrderStatus.PENDING)
                .createdAt(LocalDateTime.now().minusMinutes(11))
                .build();
    }
}
