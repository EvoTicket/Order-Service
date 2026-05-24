package com.capstone.orderservice.dto.request;

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Web3WithdrawWebhookRequest {
    private String jobId;
    private String operation;
    private String status;
    private String txHash;
    private Long blockNumber;
    private String addressContract;
    private WithdrawData data;
    private String timestamp;
    private String error;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WithdrawData {
        private String userID;
        private Long tokenId;
        private String personalWallet;
    }
}
