package com.capstone.orderservice.client;

import lombok.*;

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
