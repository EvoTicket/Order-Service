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
public class MyTicketGroupResponse {
    private Long id;
    private String eventName;
    private String date;
    private String venue;
    private String orderId;
    private Integer totalTickets;
    private String summary;
    private String statusSummary;
    private List<MyTicketItemResponse> tickets;
}
