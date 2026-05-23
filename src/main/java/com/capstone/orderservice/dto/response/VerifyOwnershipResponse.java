package com.capstone.orderservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerifyOwnershipResponse {
    private String status;
    private Long tokenId;
    private String ownerAddress;
    private Long blockNumber;
    private String timestamp;
    private String message;
}
