package com.capstone.orderservice.dto.request;

import lombok.Data;
import java.util.List;

@Data
public class BookingSessionData {
    private String userId;
    private List<BookingItem> items;

    @Data
    public static class BookingItem {
        private Long ticketTypeId;
        private Integer qty;
    }
}
