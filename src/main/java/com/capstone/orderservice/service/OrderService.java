package com.capstone.orderservice.service;

import com.capstone.orderservice.client.EventDetailResponse;
import com.capstone.orderservice.client.InventoryFeignClient;
import com.capstone.orderservice.client.ListTicketTypesInternalResponse;
import com.capstone.orderservice.dto.event.OrderConfirmEvent;
import com.capstone.orderservice.dto.event.OrderPaidEvent;
import com.capstone.orderservice.dto.event.PaymentSuccessEvent;
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
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

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
                .paymentMethod(request.getPaymentMethod())
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
    public void markPaid(String orderCode) {
        Order order = orderRepository.findByOrderCode(orderCode)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Order not found"));
        if (order.getOrderStatus() == OrderStatus.CONFIRMED) {
            log.info("Order has been marked for confirmation");
            return;
        }
        order.setOrderStatus(OrderStatus.CONFIRMED);
        orderRepository.save(order);

        redisTemplate.delete("order:reserve:" + order.getOrderCode());
    }

    @Transactional
    public void markFailed(String orderCode) {
        Order order = orderRepository.findByOrderCode(orderCode)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Order not found"));

        if (order.getOrderStatus() != OrderStatus.PENDING) return;

        order.setOrderStatus(OrderStatus.PAYMENT_FAILED);

        List<OrderItemRequest> items = order.getOrderItems()
                .stream()
                .map(item -> OrderItemRequest.builder()
                        .ticketTypeId(item.getTicketTypeId())
                        .quantity(Math.toIntExact(item.getQuantity()))
                        .build()
                )
                .toList();

        inventoryFeignClient.releaseTickets(items);

        redisTemplate.delete("order:reserve:" + orderCode);

        EventDetailResponse event = inventoryFeignClient.getEventDetail(order.getEventId()).getData();
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
        OrderConfirmEvent emailDto = OrderConfirmEvent.builder()
                .email(order.getEmail())
                .fullName(order.getFullName())
                .orderCode(order.getOrderCode())
                .totalAmount(order.getTotalAmount())
                .discountCode(
                        order.getOrderVouchers().stream()
                                .map(item -> item.getVoucher().getVoucherCode())
                                .collect(Collectors.joining(", "))
                )
                .discountAmount(order.getDiscountAmount())
                .ticketDownloadUrl("https://evoticket.vn/tickets/" + order.getOrderCode())
                .eventName(event.getEventName())
                .eventDate(
                        event.getEventStartTime().format(
                                DateTimeFormatter.ofPattern("EEEE, dd/MM/yyyy", Locale.forLanguageTag("vi"))
                        )
                )
                .eventTime(
                        event.getEventStartTime().format(timeFormatter)
                                + " - " +
                                event.getEventEndTime().format(timeFormatter)
                )
                .eventLocation(event.getVenue())
                .eventAddress(event.getAddress() != null ? event.getAddress() : "")
                .organizerName(event.getOrganizerName())
                .paymentMethod(order.getPaymentMethod().name())
                .transactionId(order.getTransactionId())
                .paidAt(order.getTransactionDateTime())
                .ticketItems(order.getOrderItems().stream()
                        .map(item -> OrderConfirmEvent.TicketItemDto.builder()
                                .ticketTypeName(item.getTicketTypeName())
                                .quantity(item.getQuantity())
                                .unitPrice(item.getUnitPrice())
                                .subtotal(item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                                .build())
                        .toList())
                .build();
        redisStreamProducer.sendMessage("order-confirm", emailDto);
    }

    @Transactional
    public void commitTicket(PaymentSuccessEvent paymentSuccessEvent){
        Order order = orderRepository.findByOrderCode(paymentSuccessEvent.getOrderCode().toString()).orElseThrow(() -> {
            log.error("Cannot find order code {}", paymentSuccessEvent.getOrderCode());
            return new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Order not found");
        });
        order.setTransactionId(paymentSuccessEvent.getTransactionId());
        order.setTransactionDateTime(paymentSuccessEvent.getTransactionDateTime());

        List<OrderItemRequest> orderItemInternalResponses = order.getOrderItems()
                .stream()
                .map(item -> OrderItemRequest.builder()
                        .ticketTypeId(item.getTicketTypeId())
                        .quantity(Math.toIntExact(item.getQuantity()))
                        .build()
                )
                .toList();
        OrderPaidEvent orderPaidEvent = OrderPaidEvent.builder()
                .orderCode(order.getOrderCode())
                .items(orderItemInternalResponses)
                .build();

        redisStreamProducer.sendMessage("order-paid", orderPaidEvent);
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