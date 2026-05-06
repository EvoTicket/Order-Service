package com.capstone.orderservice.dto.request;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class BookingSessionData {
    private String userId;
    private List<BookingItem> items;
    private String eventName;
    private LocalDateTime time;
    private String venue;

    @Data
    public static class BookingItem {
        private Long ticketTypeId;
        private Integer qty;
    }
}
