package com.capstone.orderservice.client;

import com.capstone.orderservice.dto.response.RichTicketProvenanceResponse;
import com.capstone.orderservice.dto.response.VerifyOwnershipResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
@Slf4j
public class WorkerClient {

    private static final String BASE_URL = "http://web3-worker-service:4500";
    private final RestClient restClient = RestClient.create(BASE_URL);

    /**
     * Gọi web3-worker-service để mint NFT ticket sau khi đặt vé thành công.
     */
    @Async
    public void mintOrder(Map<String, Object> payload) {
        restClient.post()
                .uri("/api/blockchain/mint-order")
                .body(payload)
                .retrieve()
                .toBodilessEntity();
        log.info("Successfully sent mint-order request to web3-worker-service");
    }

    /**
     * Gọi web3-worker-service để transfer NFT ticket sau khi resale thành công.
     */
    @Async
    public void transferTicket(Map<String, Object> payload) {
        restClient.post()
                .uri("/api/blockchain/transfer")
                .body(payload)
                .retrieve()
                .toBodilessEntity();
        log.info("Successfully sent transfer request to web3-worker-service");
    }

    @Async
    public void batchUpdateCheckInStatus(Map<String, Object> payload) {
        restClient.post()
                .uri("/api/blockchain/batch-update-checkin-status")
                .body(payload)
                .retrieve()
                .toBodilessEntity();
        log.info("Successfully sent batch-update-checkin-status request to web3-worker-service");
    }

    /**
     * Lấy lịch sử giao dịch blockchain của một ticket theo tokenId.
     */
    public RichTicketProvenanceResponse getTicketHistory(String tokenId, Long fromBlock, Long toBlock) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/blockchain/tickets/{tokenId}/history")
                        .queryParam("fromBlock", fromBlock)
                        .queryParam("toBlock", toBlock)
                        .build(tokenId))
                .retrieve()
                .body(RichTicketProvenanceResponse.class);
    }

    /**
     * Xác thực quyền sở hữu ticket trên blockchain.
     */
    public VerifyOwnershipResponse verifyOwnership(Long tokenId, String userId) {
        return restClient.post()
                .uri("/api/blockchain/verify-ownership")
                .body(Map.of("tokenId", tokenId, "userID", userId))
                .retrieve()
                .body(VerifyOwnershipResponse.class);
    }
}
