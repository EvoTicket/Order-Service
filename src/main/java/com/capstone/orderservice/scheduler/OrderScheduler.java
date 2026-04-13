package com.capstone.orderservice.scheduler;

import com.capstone.orderservice.client.InventoryFeignClient;
import com.capstone.orderservice.dto.request.OrderItemRequest;
import com.capstone.orderservice.entity.Order;
import com.capstone.orderservice.enums.OrderStatus;
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
    private final RedisTemplate<String, Object> redisTemplate;

    @Scheduled(fixedRate = 60000)
    public void releaseExpiredOrders() {

        List<Order> expired = orderRepository.findByOrderStatusAndCreatedAtBefore(
                OrderStatus.PENDING,
                LocalDateTime.now().minusMinutes(10)
        );

        for (Order order : expired) {

            String key = "order:reserve:" + order.getOrderCode();

            String json = (String) redisTemplate.opsForValue().get(key);

            if (json != null) {

                List<OrderItemRequest> items = order.getOrderItems()
                        .stream()
                        .map(item -> OrderItemRequest.builder()
                                .ticketTypeId(item.getTicketTypeId())
                                .quantity(Math.toIntExact(item.getQuantity()))
                                .build()
                        )
                        .toList();

                inventoryFeignClient.releaseTickets(items);
                log.info("Released tickets for order: {}", order.getOrderCode());
                redisTemplate.delete(key);
            }

            order.setOrderStatus(OrderStatus.EXPIRED);
            orderRepository.save(order);
        }
    }
}
