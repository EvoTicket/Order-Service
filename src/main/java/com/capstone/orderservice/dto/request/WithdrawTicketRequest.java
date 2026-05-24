package com.capstone.orderservice.dto.request;

import lombok.Data;

@Data
public class WithdrawTicketRequest {
    private Long tokenId;
    private String personalWallet;
}
