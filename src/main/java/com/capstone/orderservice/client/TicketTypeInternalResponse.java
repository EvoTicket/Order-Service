package com.capstone.orderservice.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketTypeInternalResponse {
    private Long ticketTypeId;
    private Long eventId;
    private String eventName;
    private Long showtimeId;
    private LocalDateTime eventStartTime;
    private LocalDateTime eventEndTime;
    private String venueName;
    private String venueAddress;
    private String bannerImage;
    private String ticketTypeName;
    private BigDecimal originalPrice;
}
