package com.capstone.orderservice.dto.request;

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Web3TransferWebhookRequest {
    private String jobId;
    private String operation;
    private String status;
    private String txHash;
    private Long blockNumber;
    private String addressContract;
    private TransferData data;
    private String timestamp;
    private String error;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TransferData {
        private String from_userID;
        private String to_userID;
        private String tokenId;
        private Long transferPrice;
    }
}
