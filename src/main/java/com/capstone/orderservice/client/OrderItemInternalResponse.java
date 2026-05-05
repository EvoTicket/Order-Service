package com.capstone.orderservice.client;

import com.capstone.orderservice.entity.OrderItem;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemInternalResponse {
    private String ticketTypeName;
    private Long quantity;
    private BigDecimal subTotal;

    public static OrderItemInternalResponse fromEntity(OrderItem item) {
        List<OrderItem> sameTypeItems = item.getOrder().getOrderItems()
                .stream()
                .filter(i -> i.getTicketTypeId().equals(item.getTicketTypeId()))
                .toList();

        long quantity = sameTypeItems.size();

        BigDecimal subTotal = item.getUnitPrice()
                .multiply(BigDecimal.valueOf(quantity));

        return OrderItemInternalResponse.builder()
                .ticketTypeName(item.getTicketTypeName())
                .quantity(quantity)
                .subTotal(subTotal)
                .build();
    }

    public static OrderItemInternalResponse fromlistTicketTypesInternalResponse(ListTicketTypesInternalResponse.TicketDetailResponse ticketDetailResponse) {
        return OrderItemInternalResponse.builder()
                .ticketTypeName(ticketDetailResponse.getTicketTypeName())
                .quantity(ticketDetailResponse.getQuantity())
                .subTotal(ticketDetailResponse.getPrice().multiply(BigDecimal.valueOf(ticketDetailResponse.getQuantity())))
                .build();
    }
}