package com.capstone.orderservice.service;

import com.capstone.orderservice.client.*;
import com.capstone.orderservice.dto.event.OrderConfirmEvent;
import com.capstone.orderservice.dto.event.OrderPaidEvent;
import com.capstone.orderservice.dto.event.PaymentSuccessEvent;
import com.capstone.orderservice.dto.request.BookingSessionData;
import com.capstone.orderservice.dto.request.CreateOrderRequest;
import com.capstone.orderservice.dto.request.OrderItemRequest;
import com.capstone.orderservice.dto.response.*;
import com.capstone.orderservice.entity.Order;
import com.capstone.orderservice.entity.OrderItem;
import com.capstone.orderservice.enums.OrderStatus;
import com.capstone.orderservice.enums.OrderType;
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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrderService {
    private final OrderRepository orderRepository;
    private final VoucherService voucherService;
    private final OrderUtil orderUtil;
    private final JwtUtil jwtUtil;
    private final InventoryFeignClient inventoryFeignClient;
    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisStreamProducer redisStreamProducer;
    private final ObjectMapper objectMapper;
    private final PaymentFeignClient paymentFeignClient;
    private final TicketAssetService ticketAssetService;
    private final ResaleService resaleService;

    @Transactional
    public PaymentLinkResponse createOrder(CreateOrderRequest request) {
        String sessionDataKey = "booking:data:" + request.getBookingSessionId();
        String sessionJson = (String) redisTemplate.opsForValue().get(sessionDataKey);
        if (sessionJson == null) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Booking session đã hết hạn hoặc không tồn tại");
        }

        BookingSessionData sessionData;
        try {
            sessionData = objectMapper.readValue(sessionJson, BookingSessionData.class);
        } catch (JsonProcessingException e) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "Lỗi đọc dữ liệu booking session");
        }

        Long currentUserId = jwtUtil.getDataFromAuth().userId();
        if (!Objects.equals(currentUserId, sessionData.getUserId())) {
            throw new AppException(ErrorCode.UNAUTHORIZED, "User không khớp với booking session");
        }

        List<OrderItemRequest> requestItems = sessionData.getItems().stream()
                .map(item -> OrderItemRequest.builder()
                        .ticketTypeId(item.getTicketTypeId())
                        .quantity(item.getQty())
                        .build())
                .toList();

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
                .orderType(OrderType.PRIMARY)
                .paymentMethod(request.getPaymentMethod())
                .bookingSessionId(request.getBookingSessionId())
                .build();

        ListTicketTypesInternalResponse listTicketTypesInternalResponse = inventoryFeignClient
                .getTicketTypes(requestItems).getData();
        if (listTicketTypesInternalResponse == null) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "Lấy ticket lỗi");
        }
        if(!listTicketTypesInternalResponse.isAllowDiscountCode() &&  request.getVoucherCode() != null){
            throw new AppException(ErrorCode.BAD_REQUEST, "Mã giảm giá không được áp dụng cho đơn hàng này");
        }

        order.setEventId(listTicketTypesInternalResponse.getEventId());

        for (ListTicketTypesInternalResponse.TicketDetailResponse ticket : listTicketTypesInternalResponse
                .getTicketDetails()) {

            if (ticket == null) {
                throw new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Ticket không tồn tại");
            }

            for (int i = 0; i < ticket.getQuantity(); i++) {
                String ticketCode = UUID.randomUUID().toString();
                OrderItem orderItem = OrderItem.builder()
                        .order(order)
                        .ticketTypeId(ticket.getTicketTypeId())
                        .unitPrice(ticket.getPrice())
                        .ticketTypeName(ticket.getTicketTypeName())
                        .ticketCode(ticketCode)
                        .build();

                order.addOrderItem(orderItem);
            }
        }

        BigDecimal totalAmount = calculateTotalAmount(order.getOrderItems());
        order.setTotalAmount(totalAmount);
        order.setFinalAmount(totalAmount);

        voucherService.applyVoucher(order, request.getVoucherCode());

        orderRepository.save(order);

        OrderInternalResponse requestToPayment = OrderInternalResponse.fromRequest(request, orderCode, order.getFinalAmount(),
                listTicketTypesInternalResponse);
        log.info("locale : {}", requestToPayment.getLocale());

        return paymentFeignClient.createPaymentLink(requestToPayment).getData();
    }

    @Transactional
    public void markPaid(String orderCode) {
        Order order = orderRepository.findByOrderCode(orderCode)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Order not found"));

        if (order.getOrderType() == OrderType.RESALE) {
            log.info("Order {} is a resale order; handling resale session cleanup", orderCode);
            resaleService.finalizePaidResaleOrderSession(orderCode);
            return;
        }

        if (order.getOrderStatus() == OrderStatus.CONFIRMED) {
            ticketAssetService.issueTicketsForConfirmedOrder(order);
            log.info("Order {} is already confirmed; ensured ticket assets are issued", orderCode);
            return;
        }
        order.setOrderStatus(OrderStatus.CONFIRMED);
        orderRepository.save(order);

        ticketAssetService.issueTicketsForConfirmedOrder(order);

        // Delete booking session to prevent auto-release after payment
        if (order.getBookingSessionId() != null) {
            redisTemplate.delete("booking:session:" + order.getBookingSessionId());
            redisTemplate.delete("booking:data:" + order.getBookingSessionId());
        }

        PaymentTransactionResponse payment = paymentFeignClient.getPaymentInfo(order.getOrderCode()).getData();
        EventDetailInternalResponse event = inventoryFeignClient.getEventDetailsByTicketTypeId(
                order.getOrderItems().stream()
                        .findFirst()
                        .map(OrderItem::getTicketTypeId)
                        .orElse(null))
                .getData();

        List<Map<String, Object>> ticketsPayload = order.getOrderItems().stream().map(item -> {
            Map<String, Object> ticketInfo = new HashMap<>();
            ticketInfo.put("ticketCode", item.getTicketCode());
            ticketInfo.put("regularPrice", item.getUnitPrice());
            String eventName = event != null && event.getEventName() != null ? event.getEventName() : "Unknown Event";
            ticketInfo.put("eventInfo", eventName + " - " + item.getTicketTypeName());
            return ticketInfo;
        }).toList();

        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", String.valueOf(order.getUserId()));
        payload.put("orderId", order.getOrderCode());
        payload.put("tickets", ticketsPayload);

        TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        RestClient restClient = RestClient.create("http://web3-worker-service:4500");
                        restClient.post()
                                .uri("/api/blockchain/mint-order")
                                .body(payload)
                                .retrieve()
                                .toBodilessEntity();
                        log.info("Successfully sent mint-order request to web3-worker-service for order {}", order.getOrderCode());
                    } catch (Exception e) {
                        log.error("Failed to call web3-worker-service for minting order {}", order.getOrderCode(), e);
                    }
                }
            }
        );

        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("EEEE, dd/MM/yyyy", Locale.forLanguageTag("vi"));
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");

        EventDetailInternalResponse.ShowtimeDetail showtime = event.getShowtime();

        String showtimeDate = "";
        String showtimeTime = "";
        String showtimeLocation = "";
        String showtimeAddress = "";

        if (showtime != null) {
            showtimeDate = showtime.getStartDatetime().format(dateFormatter);

            showtimeTime = showtime.getStartDatetime().format(timeFormatter)
                    + " - "
                    + showtime.getEndDatetime().format(timeFormatter);

            showtimeLocation = showtime.getVenue();
            showtimeAddress = showtime.getFullAddress();
        } else if (event.getEventStartTime() != null) {
            showtimeDate = event.getEventStartTime().format(dateFormatter);

            showtimeTime = event.getEventStartTime().format(timeFormatter)
                    + " - "
                    + event.getEventEndTime().format(timeFormatter);

            showtimeLocation = event.getVenue();
            showtimeAddress = event.getAddress();
        }

        OrderConfirmEvent emailDto = OrderConfirmEvent.builder()
                .email(order.getEmail())
                .fullName(order.getFullName())
                .orderCode(order.getOrderCode())
                .totalAmount(order.getTotalAmount())
                .discountCode(
                        order.getOrderVouchers().stream()
                                .map(item -> item.getVoucher().getVoucherCode())
                                .collect(Collectors.joining(", ")))
                .discountAmount(order.getDiscountAmount())
                .ticketDownloadUrl("https://evoticket.vn/tickets/" + order.getOrderCode())
                .eventName(event.getEventName())
                .eventDate(event.getEventStartTime() != null
                        ? event.getEventStartTime().format(dateFormatter)
                        : "")
                .eventTime(event.getEventStartTime() != null
                        ? event.getEventStartTime().format(timeFormatter) + " - "
                                + event.getEventEndTime().format(timeFormatter)
                        : "")
                .eventLocation(event.getVenue())
                .eventAddress(event.getAddress() != null ? event.getAddress() : "")
                .organizerName(event.getOrganizerName())
                .showtimeDate(showtimeDate)
                .showtimeTime(showtimeTime)
                .showtimeLocation(showtimeLocation)
                .showtimeAddress(showtimeAddress)
                .paymentMethod(order.getPaymentMethod().name())
                .transactionId(payment.getTransactionId())
                .paidAt(payment.getTransactionDateTime())
                .ticketItems(order.getOrderItems().stream()
                        .collect(Collectors.groupingBy(OrderItem::getTicketTypeId))
                        .values().stream()
                        .map(itemsList -> {
                            OrderItem first = itemsList.getFirst();
                            long totalQuantity = itemsList.size();
                            return OrderConfirmEvent.TicketItemDto.builder()
                                    .ticketTypeName(first.getTicketTypeName())
                                    .quantity(totalQuantity)
                                    .unitPrice(first.getUnitPrice())
                                    .subtotal(first.getUnitPrice().multiply(BigDecimal.valueOf(totalQuantity)))
                                    .build();
                        })
                        .toList())
                .build();
        redisStreamProducer.sendMessage("order-confirm", emailDto);
    }

    @Transactional
    public void markFailed(String orderCode) {
        Order order = orderRepository.findByOrderCode(orderCode)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Order not found"));
        OrderType orderType = order.getOrderType() != null ? order.getOrderType() : OrderType.PRIMARY;

        if (orderType == OrderType.RESALE) {
            resaleService.markResalePaymentFailed(order);
            return;
        }

        if (order.getOrderStatus() != OrderStatus.PENDING)
            return;

        order.setOrderStatus(OrderStatus.PAYMENT_FAILED);

        List<OrderItemRequest> items = order.getOrderItems()
                .stream()
                .collect(Collectors.groupingBy(OrderItem::getTicketTypeId))
                .entrySet().stream()
                .map(entry -> OrderItemRequest.builder()
                        .ticketTypeId(entry.getKey())
                        .quantity(entry.getValue().size())
                        .build())
                .toList();

        inventoryFeignClient.releaseTickets(items);

        if (order.getBookingSessionId() != null) {
            redisTemplate.delete("booking:session:" + order.getBookingSessionId());
            redisTemplate.delete("booking:data:" + order.getBookingSessionId());
        }
    }

    @Transactional
    public void commitTicket(PaymentSuccessEvent paymentSuccessEvent) {
        Order order = orderUtil.getOrderByOrderCode(paymentSuccessEvent.getOrderCode());
        OrderType orderType = order.getOrderType() != null ? order.getOrderType() : OrderType.PRIMARY;

        if (orderType == OrderType.RESALE) {
            resaleService.finalizePaidResaleOrder(order, paymentSuccessEvent);
            return;
        }

        List<OrderItemRequest> orderItemInternalResponses = order.getOrderItems()
                .stream()
                .collect(Collectors.groupingBy(OrderItem::getTicketTypeId))
                .entrySet().stream()
                .map(entry -> OrderItemRequest.builder()
                        .ticketTypeId(entry.getKey())
                        .quantity(entry.getValue().size())
                        .build())
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
    public void cancelOrder(String orderCode) {
        Order order = orderUtil.getOrderByOrderCode(orderCode);
        OrderType orderType = order.getOrderType() != null ? order.getOrderType() : OrderType.PRIMARY;

        if (orderType == OrderType.RESALE) {
            resaleService.cancelPendingResaleOrder(order);
            return;
        }

        if (!order.getOrderStatus().canBeCancelled()) {
            throw new AppException(
                    ErrorCode.BAD_REQUEST,
                    "Không thể hủy đơn ở trạng thái " + order.getOrderStatus());
        }

        List<OrderItemRequest> items = order.getOrderItems()
                .stream()
                .collect(Collectors.groupingBy(OrderItem::getTicketTypeId))
                .entrySet().stream()
                .map(entry -> OrderItemRequest.builder()
                        .ticketTypeId(entry.getKey())
                        .quantity(entry.getValue().size())
                        .build())
                .toList();

        boolean releaseSuccess = Boolean.TRUE.equals(
                inventoryFeignClient.releaseTickets(items).getData());

        boolean cancelSuccess = Boolean.TRUE.equals(
                paymentFeignClient.cancelPayment(order.getOrderCode()).getData());

        if (!releaseSuccess || !cancelSuccess) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "Huỷ đơn thất bại ở inventory hoặc payment");
        }

        if (order.getBookingSessionId() != null) {
            redisTemplate.delete("booking:session:" + order.getBookingSessionId());
            redisTemplate.delete("booking:data:" + order.getBookingSessionId());
        }

        order.setOrderStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);
    }

    @Transactional(readOnly = true)
    public OrderInternalResponse getOrdersDetail(String orderCode) {
        Order order = orderRepository.findByOrderCode(orderCode)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Order not found"));
        return OrderInternalResponse.fromEntity(order);
    }

    private BigDecimal calculateTotalAmount(List<OrderItem> items) {
        return items.stream()
                .map(OrderItem::getUnitPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    @Transactional(readOnly = true)
    public Map<Long, EventVolumeDto> getVolumeForEvents(List<Long> eventIds) {
        if (eventIds == null || eventIds.isEmpty()) {
            return Collections.emptyMap();
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime oneDayAgo = now.minusDays(1);
        LocalDateTime twoDaysAgo = now.minusDays(2);

        List<Object[]> results = orderRepository.calculateVolumeForEvents(eventIds, oneDayAgo, twoDaysAgo);

        return results.stream()
                .map(row -> {
                    Long eventId = (Long) row[0];
                    BigDecimal volume24h = row[1] != null ? (BigDecimal) row[1] : BigDecimal.ZERO;
                    BigDecimal volumePrev24h = row[2] != null ? (BigDecimal) row[2] : BigDecimal.ZERO;

                    return EventVolumeDto.builder()
                            .eventId(eventId)
                            .volume24h(volume24h)
                            .hotness(getHotness(volume24h, volumePrev24h))
                            .build();
                })
                .collect(Collectors.toMap(EventVolumeDto::getEventId, dto -> dto));
    }

    private double getHotness(BigDecimal volume24h, BigDecimal volumePrev24h) {
        if (volumePrev24h.compareTo(BigDecimal.ZERO) > 0) {
            return volume24h.subtract(volumePrev24h)
                    .divide(volumePrev24h, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .doubleValue();
        } else if (volume24h.compareTo(BigDecimal.ZERO) > 0) {
            return 100.0;
        } else {
            return 0.0;
        }
    }

    @Transactional(readOnly = true)
    public List<Long> getPurchasedEventIdsByUserId(Long userId) {
        return orderRepository.findPurchasedEventIdsByUserId(userId);
    }

    public Map<Long, BigDecimal> getRevenueMap(List<Long> eventIds) {
        List<Object[]> results = orderRepository.getRevenueAllTime(eventIds);

        Map<Long, BigDecimal> map = new HashMap<>();

        for (Object[] row : results) {
            Long eventId = (Long) row[0];
            BigDecimal revenue = (BigDecimal) row[1];
            map.put(eventId, revenue);
        }

        for (Long id : eventIds) {
            map.putIfAbsent(id, BigDecimal.ZERO);
        }

        return map;
    }

    public PlatformStatsResponse getPlatformStats(int days) {
        BigDecimal totalGmv = orderRepository.getTotalGmvAllTime();
        long totalTicketsSold = orderRepository.getTotalTicketsSoldAllTime();

        // Giả sử doanh thu phí sàn là 2.5% của GMV (có thể điều chỉnh hoặc tính toán từ
        // ResaleService)
        BigDecimal totalRevenue = totalGmv.multiply(new BigDecimal("0.025")).setScale(2, RoundingMode.HALF_UP);

        LocalDateTime startDate = LocalDateTime.now().minusDays(days);
        List<Object[]> dailyResults = orderRepository.getDailyStats(startDate);

        List<DailyStatsDto> trend = dailyResults.stream()
                .map(row -> DailyStatsDto.builder()
                        .date(((java.sql.Date) row[0]).toLocalDate())
                        .gmv((BigDecimal) row[1])
                        .ticketsSold((long) row[2])
                        .build())
                .toList();

        return PlatformStatsResponse.builder()
                .totalGmv(totalGmv)
                .totalRevenue(totalRevenue)
                .totalTicketsSold(totalTicketsSold)
                .trend(trend)
                .build();
    }
}
