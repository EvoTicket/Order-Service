package com.capstone.orderservice.client;

import com.capstone.orderservice.entity.OrderItem;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemInternalResponse {
    private Long id;
    private Long ticketTypeId;
    private String ticketTypeName;
    private Long quantity;
    private BigDecimal unitPrice;
    private BigDecimal subtotal;
    private String ticketCode;
    private String tokenId;
    private LocalDateTime createdAt;

    public static OrderItemInternalResponse fromEntity(OrderItem item) {
        return OrderItemInternalResponse.builder()
                .id(item.getId())
                .ticketTypeId(item.getTicketTypeId())
                .ticketTypeName(item.getTicketTypeName())
                .quantity(1L)
                .unitPrice(item.getUnitPrice())
                .subtotal(item.getUnitPrice())
                .ticketCode(item.getTicketCode())
                .tokenId(item.getTokenId())
                .createdAt(item.getCreatedAt())
                .build();
    }
}