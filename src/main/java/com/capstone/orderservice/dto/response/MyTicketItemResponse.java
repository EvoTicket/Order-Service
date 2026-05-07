package com.capstone.orderservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MyTicketItemResponse {
    private Long id;
    private String ticketName;
    private String ticketType;
    private String seat;
    private String ticketCode;
    private String tokenId;
    private String status;
    private String listingCode;
}
