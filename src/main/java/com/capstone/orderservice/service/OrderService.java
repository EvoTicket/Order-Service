package com.capstone.orderservice.service;

import com.capstone.orderservice.client.InventoryFeignClient;
import com.capstone.orderservice.dto.OrderCreationEvent;
import com.capstone.orderservice.dto.request.CreateOrderRequest;
import com.capstone.orderservice.dto.request.OrderItemRequest;
import com.capstone.orderservice.dto.response.OrderResponse;
import com.capstone.orderservice.entity.Order;
import com.capstone.orderservice.entity.OrderItem;
import com.capstone.orderservice.enums.OrderStatus;
import com.capstone.orderservice.exception.AppException;
import com.capstone.orderservice.exception.ErrorCode;
import com.capstone.orderservice.producer.RedisStreamProducer;
import com.capstone.orderservice.repository.OrderRepository;
import com.capstone.orderservice.security.JwtUtil;
import com.capstone.orderservice.util.OrderUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {
    private final OrderRepository orderRepository;
    private final VoucherService voucherService;
    private final OrderUtil orderUtil;
    private final JwtUtil jwtUtil;
    private final InventoryFeignClient inventoryFeignClient;
    private final RedisStreamProducer redisStreamProducer;

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        String orderCode = "ORD-" + UUID.randomUUID();

        Order order = Order.builder()
                .orderCode(orderCode)
                .userId(jwtUtil.getDataFromAuth().userId())
                .orderCode(orderCode)
                .discountAmount(BigDecimal.ZERO)
                .orderStatus(OrderStatus.PENDING)
                .build();

        for (OrderItemRequest itemRequest : request.getItems()) {

            var ticketResponse = inventoryFeignClient
                    .getTicketTypeById(itemRequest.getTicketTypeId());

            if (ticketResponse == null || ticketResponse.getData() == null) {
                throw new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Ticket không tồn tại");
            }

            BigDecimal unitPrice = ticketResponse.getData().getPrice();

            OrderItem orderItem = OrderItem.builder()
                    .order(order)
                    .ticketTypeId(itemRequest.getTicketTypeId())
                    .quantity(itemRequest.getQuantity())
                    .unitPrice(unitPrice)
                    .build();

            orderItem.calculateSubtotal();
            order.addOrderItem(orderItem);
        }

        BigDecimal totalAmount = calculateTotalAmount(order.getOrderItems());
        order.setTotalAmount(totalAmount);
        order.setFinalAmount(totalAmount);

        voucherService.applyVouchers(order, request.getVoucherIds());

        Order savedOrder = orderRepository.save(order);

        OrderCreationEvent orderCreationEvent = OrderCreationEvent.builder()
                .id(savedOrder.getId())
                .userId(jwtUtil.getDataFromAuth().userId())
                .orderCode(savedOrder.getOrderCode())
                .totalAmount(totalAmount)
                .discountAmount(totalAmount)
                .finalAmount(totalAmount)
                .build();
        redisStreamProducer.sendMessage("order-creation", orderCreationEvent);

        return OrderResponse.fromEntity(orderRepository.save(order));
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderByOrderId(Long id) {
        Order order = orderUtil.getOrderById(id);
        return OrderResponse.fromEntity(order);
    }


    @Transactional(readOnly = true)
    public Page<OrderResponse> getOrdersByUserId(Pageable pageable) {
        Long userId = jwtUtil.getDataFromAuth().userId();
        return orderRepository.findByUserId(userId, pageable)
                .map(OrderResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> getAllOrders(Pageable pageable) {
        return orderRepository.findAll(pageable)
                .map(OrderResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> getOrdersByStatus(OrderStatus status, Pageable pageable) {
        return orderRepository.findByOrderStatus(status, pageable)
                .map(OrderResponse::fromEntity);
    }


    @Transactional
    public void cancelOrder(Long id) {
        Order order = orderUtil.getOrderById(id);

        if (order.getOrderStatus() == OrderStatus.CONFIRMED) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Không thể hủy đơn hàng đã được xác nhận");
        }

        if (order.getOrderStatus() == OrderStatus.CANCELLED) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Đơn hàng đã bị hủy trước đó");
        }

        order.setOrderStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);
    }

    private BigDecimal calculateTotalAmount(List<OrderItem> items) {
        return items.stream()
                .map(item -> item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }
}