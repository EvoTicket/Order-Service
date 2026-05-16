package com.capstone.orderservice.dto.response;

import com.capstone.orderservice.enums.TicketAccessStatus;
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
    private TicketInfo ticketInfo;
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
    public static class TicketInfo {
        private String eventName;
        private String eventDate;
        private String eventTime;
        private String venue;
        private String ticketType;
        private String seat;
        private String ticketCode;
        private String tokenId;
        private String status;
        private String originalPrice;
        private String mintStatus;
        private TicketAccessStatus checkInStatus;
    }

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
