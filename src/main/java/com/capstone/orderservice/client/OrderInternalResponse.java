package com.capstone.orderservice.client;

import com.capstone.orderservice.dto.response.OrderItemResponse;
import com.capstone.orderservice.entity.Order;
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
    private Long id;
    private String orderCode;
    private String eventName;
    private String buyerName;
    private String buyerPhone;
    private String buyerEmail;
    private BigDecimal finalAmount;
    private List<OrderItemResponse> items;

    public static OrderInternalResponse fromEntity(Order order) {
        return OrderInternalResponse.builder()
                .id(order.getId())
                .orderCode(order.getOrderCode())
                .eventName(order.getEventName())
                .buyerEmail(order.getEmail())
                .buyerName(order.getFullName())
                .buyerPhone(order.getPhoneNumber())
                .finalAmount(order.getFinalAmount())
                .items(order.getOrderItems().stream()
                        .map(OrderItemResponse::fromEntity)
                        .toList())
                .build();
    }
}
