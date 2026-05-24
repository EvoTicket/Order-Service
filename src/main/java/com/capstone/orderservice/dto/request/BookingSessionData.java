package com.capstone.orderservice.dto.request;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class BookingSessionData {
    private Long userId;
    private List<BookingItem> items;
    private String eventName;
    private LocalDateTime time;
    private String venue;
    private BigDecimal totalAmount;
    private boolean allowDiscountCode;

    @Data
    public static class BookingItem {
        private Long ticketTypeId;
        private Integer qty;
        private BigDecimal price;
    }
}
