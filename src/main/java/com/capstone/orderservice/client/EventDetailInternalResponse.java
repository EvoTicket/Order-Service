package com.capstone.orderservice.client;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventDetailInternalResponse {
    String eventName;
    LocalDateTime eventStartTime;
    LocalDateTime eventEndTime;
    String venue;
    String address;
    String organizerName;
    String category;
    Integer provinceCode;
    ShowtimeDetail showtime;
    BigDecimal maxResalePricePercentage;
    BigDecimal organizerRoyaltyFeePercentage;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ShowtimeDetail {
        Long showtimeId;
        LocalDateTime startDatetime;
        LocalDateTime endDatetime;
        String venue;
        String address;
        String fullAddress;
        Integer provinceCode;
    }
}
