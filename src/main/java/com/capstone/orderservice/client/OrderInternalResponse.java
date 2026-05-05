package com.capstone.orderservice.client;

import com.capstone.orderservice.dto.request.CreateOrderRequest;
import com.capstone.orderservice.entity.Order;
import com.capstone.orderservice.enums.PaymentMethod;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderInternalResponse {
    private PaymentMethod paymentMethod;
    private String orderCode;
    private String buyerName;
    private String buyerPhone;
    private String buyerEmail;
    private Long eventId;
    private String locale;
    private BigDecimal finalAmount;
    private List<OrderItemInternalResponse> items;

    public static OrderInternalResponse fromEntity(Order order) {
        return OrderInternalResponse.builder()
                .orderCode(order.getOrderCode())
                .paymentMethod(order.getPaymentMethod())
                .buyerEmail(order.getEmail())
                .buyerName(order.getFullName())
                .buyerPhone(order.getPhoneNumber())
                .eventId(order.getEventId())
                .finalAmount(order.getFinalAmount())
                .items(order.getOrderItems().stream()
                        .map(OrderItemInternalResponse::fromEntity)
                        .toList())
                .build();
    }

    public static OrderInternalResponse fromRequest(
            CreateOrderRequest request,
            String orderCode,
            BigDecimal finalAmount,
            ListTicketTypesInternalResponse tickets
    ) {
        return OrderInternalResponse.builder()
                .orderCode(orderCode)
                .paymentMethod(request.getPaymentMethod())
                .buyerEmail(request.getEmail())
                .buyerName(request.getFullName())
                .buyerPhone(request.getPhoneNumber())
                .eventId(tickets.getEventId())
                .locale(request.getLocale())
                .finalAmount(finalAmount)
                .items(tickets.ticketDetails.stream().map(OrderItemInternalResponse::fromlistTicketTypesInternalResponse).toList())
                .build();
    }
}
