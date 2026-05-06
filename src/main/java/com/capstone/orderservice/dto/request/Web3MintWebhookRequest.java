package com.capstone.orderservice.dto.request;

import lombok.Data;

@Data
public class Web3MintWebhookRequest {
    private String jobId;
    private String operation;
    private String status;
    private String txHash;
    private Web3MintData data;
    private String timestamp;
    private String error;

    @Data
    public static class Web3MintData {
        private String ticketCode;
        private String tokenId;
        private JobContext jobContext;
        private ChainCommand chainCommand;
    }

    @Data
    public static class JobContext {
        private String userId;
        private String orderId;
        private String ticketCode;
    }

    @Data
    public static class ChainCommand {
        private String toWallet;
        private Long basePrice;
        private String metadataURI;
    }
}
