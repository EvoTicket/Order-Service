package com.capstone.orderservice.dto.request;

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Web3BatchCheckInWebhookRequest {
    private String jobId;
    private String operation;
    private String status;
    private Object data;
    private String timestamp;
    private Integer processedCount;
    private Integer failedCount;
    private Integer totalRequested;
    private List<TicketResult> tickets;
    private List<TicketError> errors;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TicketResult {
        private String tokenId;
        private String ticketCode;
        private String checkinTime;
        private String status;
        private String transactionHash;
        private Long blockNumber;
        private String updatedAt;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TicketError {
        private String ticketCode;
        private String tokenId;
        private String error;
    }
}
