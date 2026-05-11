package com.capstone.orderservice.dto.response;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class BlockchainProvenanceDto {
    private String tokenId;
    private Map<String, Object> metadata;
    private String currentOwner;
    private Integer totalTransfers;
    private List<BlockchainHistoryEntry> history;
    private Long fromBlock;
    private Long toBlock;

    @Data
    public static class BlockchainHistoryEntry {
        private String transactionHash;
        private Long blockNumber;
        private String event;
        private String from;
        private String to;
        private String timestamp;
    }
}
