package com.capstone.orderservice.controller;

import com.capstone.orderservice.dto.request.Web3MintWebhookRequest;
import com.capstone.orderservice.service.TicketAssetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/webhook/web3")
@RequiredArgsConstructor
@Slf4j
public class Web3WebhookController {

    private final TicketAssetService ticketAssetService;

    @PostMapping("/mint-ticket")
    public ResponseEntity<String> handleMintTicketWebhook(@RequestBody Web3MintWebhookRequest request) {
        log.info("Received web3 mint webhook for job: {}", request.getJobId());
        ticketAssetService.handleWeb3MintWebhook(request);
        return ResponseEntity.ok("Webhook received");
    }
}
