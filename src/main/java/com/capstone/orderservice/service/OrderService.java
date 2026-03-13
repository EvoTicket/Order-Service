package com.capstone.orderservice.service;

import com.capstone.orderservice.client.InventoryFeignClient;
import com.capstone.orderservice.client.ListTicketTypesInternalResponse;
import com.capstone.orderservice.dto.event.OrderPaidEvent;
import com.capstone.orderservice.dto.request.CreateOrderRequest;
import com.capstone.orderservice.client.OrderInternalResponse;
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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {
    private final OrderRepository orderRepository;
    private final VoucherService voucherService;
    private final OrderUtil orderUtil;
    private final JwtUtil jwtUtil;
    private final InventoryFeignClient inventoryFeignClient;
    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisStreamProducer redisStreamProducer;
    private final ObjectMapper objectMapper;

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        inventoryFeignClient.reserveTickets(request.getItems());

        String datePart = LocalDate.now()
                .format(DateTimeFormatter.ofPattern("ddMMyy"));

        int randomPart = ThreadLocalRandom.current().nextInt(100000, 1_000_000);

        String orderCode = datePart + randomPart;

        Order order = Order.builder()
                .orderCode(orderCode)
                .userId(jwtUtil.getDataFromAuth().userId())
                .fullName(request.getFullName())
                .phoneNumber(request.getPhoneNumber())
                .email(request.getEmail())
                .orderCode(orderCode)
                .discountAmount(BigDecimal.ZERO)
                .orderStatus(OrderStatus.PENDING)
                .build();

        ListTicketTypesInternalResponse response = inventoryFeignClient.getTicketTypes(request.getItems()).getData();
        if (response == null) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "Lấy ticket lỗi");
        }
        order.setEventId(response.getEventId());
        order.setEventName(response.getEventName());

        for (ListTicketTypesInternalResponse.TicketDetailResponse ticket : response.getTicketDetails()) {

            if (ticket == null) {
                throw new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Ticket không tồn tại");
            }

            OrderItem orderItem = OrderItem.builder()
                    .order(order)
                    .ticketTypeId(ticket.getTicketTypeId())
                    .quantity(ticket.getQuantity())
                    .unitPrice(ticket.getPrice())
                    .ticketTypeName(ticket.getTicketTypeName())
                    .build();

            orderItem.calculateSubtotal();
            order.addOrderItem(orderItem);
        }

        BigDecimal totalAmount = calculateTotalAmount(order.getOrderItems());
        order.setTotalAmount(totalAmount);
        order.setFinalAmount(totalAmount);

        voucherService.applyVouchers(order, request.getVoucherIds());

        try {
            String json = objectMapper.writeValueAsString(request.getItems());
            redisTemplate.opsForValue().set(
                    "order:reserve:" + orderCode,
                    json,
                    Duration.ofMinutes(10)
            );
        } catch (JsonProcessingException e) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "Lỗi khi chuyển đổi dữ liệu đơn hàng");
        }

        return OrderResponse.fromEntity(orderRepository.save(order));
    }

    @Transactional
    public void markPaid(Long orderId) {

        Order order = orderRepository.findById(orderId)
                .orElseThrow();

        order.setOrderStatus(OrderStatus.CONFIRMED);
        orderRepository.save(order);

        List<OrderItemRequest> orderItemInternalResponses = order.getOrderItems()
                .stream()
                .map(item -> OrderItemRequest.builder()
                        .ticketTypeId(item.getTicketTypeId())
                        .quantity(Math.toIntExact(item.getQuantity()))
                        .build()
                )
                .toList();
        OrderPaidEvent orderPaidEvent = OrderPaidEvent.builder()
                .items(orderItemInternalResponses)
                .build();

        redisStreamProducer.sendMessage("order-paid", orderPaidEvent);

        redisTemplate.delete("order:reserve:" + order.getOrderCode());
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

        if (!order.getOrderStatus().canBeCancelled()) {
            throw new AppException(
                    ErrorCode.BAD_REQUEST,
                    "Không thể hủy đơn ở trạng thái " + order.getOrderStatus()
            );
        }

        List<OrderItemRequest> items = order.getOrderItems()
                .stream()
                .map(item -> OrderItemRequest.builder()
                        .ticketTypeId(item.getTicketTypeId())
                        .quantity(Math.toIntExact(item.getQuantity()))
                        .build()
                )
                .toList();

        inventoryFeignClient.releaseTickets(items);

        order.setOrderStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);
    }

    @Transactional(readOnly = true)
    public OrderInternalResponse getOrdersDetail(Long id) {
        Order order = orderUtil.getOrderById(id);
        return OrderInternalResponse.fromEntity(order);
    }

    private BigDecimal calculateTotalAmount(List<OrderItem> items) {
        return items.stream()
                .map(item -> item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }
}