package com.capstone.orderservice.dto.response;

import com.capstone.orderservice.client.OrderItemInternalResponse;
import com.capstone.orderservice.entity.Order;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.capstone.orderservice.enums.OrderStatus;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {
    private Long id;
    private Long userId;
    private String orderCode;
    private BigDecimal totalAmount;
    private BigDecimal discountAmount;
    private BigDecimal finalAmount;
    private OrderStatus orderStatus;
    private List<VoucherResponse> vouchers;
    private List<OrderItemInternalResponse> items;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static OrderResponse fromEntity(Order order) {
        return OrderResponse.builder()
                .id(order.getId())
                .orderCode(order.getOrderCode())
                .userId(order.getUserId())
                .orderCode(order.getOrderCode())
                .totalAmount(order.getTotalAmount())
                .discountAmount(order.getDiscountAmount())
                .finalAmount(order.getFinalAmount())
                .orderStatus(order.getOrderStatus())
                .vouchers(order.getOrderVouchers().stream()
                        .map(VoucherResponse::fromOrderVoucher)
                        .toList())
                .items(order.getOrderItems().stream()
                        .map(OrderItemInternalResponse::fromEntity)
                        .toList())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }
}