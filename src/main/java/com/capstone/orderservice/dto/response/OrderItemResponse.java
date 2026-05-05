package com.capstone.orderservice.dto.response;

import com.capstone.orderservice.entity.OrderItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemResponse {
    private Long id;
    private Long ticketTypeId;
    private String ticketTypeName;
    private Long quantity;
    private BigDecimal unitPrice;
    private BigDecimal subtotal;
    private String ticketCode;
    private String tokenId;
    private LocalDateTime createdAt;

    public static OrderItemResponse fromEntity(OrderItem item) {
        return OrderItemResponse.builder()
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
