package com.capstone.orderservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RichTicketProvenanceResponse {
    private Object tokenId;
    private BlockchainInfo blockchain;
    private List<HistoryEntry> history;
    private List<Map<String, Object>> events;
    private String fromBlock;
    private String toBlock;
    private Integer totalEvents;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BlockchainInfo {
        private String network;
        private String onChainStatus;
        private String transactionHash;
        private String lastUpdated;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HistoryEntry {
        private String type;
        private String title;
        private String description;
        private String timestamp;
        private Map<String, Object> details;
    }
}
