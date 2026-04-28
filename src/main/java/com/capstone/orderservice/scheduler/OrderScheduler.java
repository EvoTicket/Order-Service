package com.capstone.orderservice.scheduler;

import com.capstone.orderservice.client.InventoryFeignClient;
import com.capstone.orderservice.client.PaymentFeignClient;
import com.capstone.orderservice.dto.request.OrderItemRequest;
import com.capstone.orderservice.entity.Order;
import com.capstone.orderservice.enums.OrderStatus;
import com.capstone.orderservice.exception.AppException;
import com.capstone.orderservice.exception.ErrorCode;
import com.capstone.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderScheduler {
    private final OrderRepository orderRepository;
    private final InventoryFeignClient inventoryFeignClient;
    private final PaymentFeignClient paymentFeignClient;
    private final RedisTemplate<String, Object> redisTemplate;

    @Scheduled(fixedRate = 60000)
    public void releaseExpiredOrders() {

        List<Order> expired = orderRepository.findByOrderStatusAndCreatedAtBefore(
                OrderStatus.PENDING,
                LocalDateTime.now().minusMinutes(10)
        );

        for (Order order : expired) {
            String sessionKey = "booking:session:" + order.getBookingSessionId();
            Boolean sessionExists = redisTemplate.hasKey(sessionKey);

            if (sessionExists != null && !sessionExists) {
                // Session has expired or been deleted, mark order as expired
                log.info("Booking session expired for order: {}, marking as EXPIRED", order.getOrderCode());
                paymentFeignClient.cancelPayment(order.getOrderCode());
                order.setOrderStatus(OrderStatus.EXPIRED);
                orderRepository.save(order);
            }
        }
    }
}
