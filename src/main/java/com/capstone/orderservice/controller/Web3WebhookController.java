package com.capstone.orderservice.controller;

import com.capstone.orderservice.dto.request.Web3MintWebhookRequest;
import com.capstone.orderservice.service.TicketAssetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/webhook/web3")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Webhook Web3", description = "Các endpoint nhận thông báo từ hệ thống Blockchain Worker")
public class Web3WebhookController {

    private final TicketAssetService ticketAssetService;

    @Operation(summary = "Nhận kết quả Mint vé", description = "Được gọi bởi Web3 Worker khi quá trình mint vé trên blockchain hoàn tất.")
    @PostMapping("/mint-ticket")
    public ResponseEntity<String> handleMintTicketWebhook(@RequestBody Web3MintWebhookRequest request) {
        log.info("Received web3 mint webhook for job: {}", request.getJobId());
        ticketAssetService.handleWeb3MintWebhook(request);
        return ResponseEntity.ok("Webhook received");
    }
}
