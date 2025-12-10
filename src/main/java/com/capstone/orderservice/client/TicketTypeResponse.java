package com.capstone.orderservice.client;

import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketTypeResponse {
    private Long ticketTypeId;
    private String typeName;
    private String description;
    private BigDecimal price;
    private OffsetDateTime takePlaceTime;
    private Integer quantityAvailable;
    private Integer quantitySold;
    private Integer minPurchase;
    private Integer maxPurchase;
    private OffsetDateTime saleStartDate;
    private OffsetDateTime saleEndDate;
    private TicketTypeStatus ticketTypeStatus;
    private Long eventId;
}