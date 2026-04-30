package com.capstone.orderservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MyTicketsResponse {
    private Long totalTickets;
    private Long activeCount;
    private Long usedCount;
    private Long mintPendingCount;
    private Long onSaleCount;
    private List<TicketAssetResponse> tickets;
}
