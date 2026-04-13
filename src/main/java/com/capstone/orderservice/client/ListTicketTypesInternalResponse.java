package com.capstone.orderservice.client;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ListTicketTypesInternalResponse {
    Long eventId;
    String eventName;
    Long showtimeId;
    List<TicketDetailResponse> ticketDetails;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TicketDetailResponse {
        Long ticketTypeId;
        String ticketTypeName;
        Long quantity;
        BigDecimal price;
    }
}
