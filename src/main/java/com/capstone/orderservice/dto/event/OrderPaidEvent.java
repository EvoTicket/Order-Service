package com.capstone.orderservice.dto.event;

import com.capstone.orderservice.client.OrderItemInternalResponse;
import com.capstone.orderservice.dto.request.OrderItemRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderPaidEvent {
    private List<OrderItemRequest> items;
}
