package com.capstone.orderservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

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
    private String contractAddress;
    private Long fromBlock;
    private Long toBlock;
    private String status;
    private String listingCode;
    private BigDecimal listingPrice;
}
