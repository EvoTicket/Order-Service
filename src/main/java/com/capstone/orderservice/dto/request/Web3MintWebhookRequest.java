package com.capstone.orderservice.dto.request;

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Web3MintWebhookRequest {
    private String jobId;
    private String orderId; // Support flat structure
    private String operation;
    private String status;
    private String txHash;
    private Long blockNumber;
    private String contractAddress;
    private List<TicketResult> tickets; // Support flat structure
    private Integer queuedJobs; // Support flat structure
    private Web3MintData data;
    private String timestamp;
    private String error;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Web3MintData {
        private String ticketCode;
        private String tokenId;
        private String metadataURI;
        private JobContext jobContext;
        private ChainCommand chainCommand;
        private String contractAddress;
        
        // Order-level fields
        private String orderId;
        private List<TicketResult> tickets;
        private Integer queuedJobs;
    }
    
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TicketResult {
        private String tokenId;
        private String ticketCode;
        private String txHash;
        private Long blockNumber;
        private String contractAddress;
        private String metadataURI;
        private ChainCommand chainCommand;
        private String error;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JobContext {
        private String userId;
        private String orderId;
        private String ticketCode;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ChainCommand {
        private String toWallet;
        private Long basePrice;
        private String metadataURI;
        private String ticketRefHash;
        private String orderRefHash;
    }
}
