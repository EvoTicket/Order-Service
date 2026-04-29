package com.capstone.orderservice.controller;

import com.capstone.orderservice.dto.BaseResponse;
import com.capstone.orderservice.dto.response.MyTicketsResponse;
import com.capstone.orderservice.dto.response.ResaleEligibilityResponse;
import com.capstone.orderservice.dto.response.TicketAssetResponse;
import com.capstone.orderservice.service.TicketAssetService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tickets")
@RequiredArgsConstructor
public class TicketController {
    private final TicketAssetService ticketAssetService;

    @GetMapping("/me")
    public ResponseEntity<BaseResponse<MyTicketsResponse>> getMyTickets() {
        return ResponseEntity.ok(BaseResponse.ok("Fetched my tickets successfully", ticketAssetService.getMyTickets()));
    }

    @GetMapping("/{ticketAssetId}")
    public ResponseEntity<BaseResponse<TicketAssetResponse>> getTicketDetail(@PathVariable Long ticketAssetId) {
        return ResponseEntity.ok(BaseResponse.ok("Fetched ticket detail successfully",
                ticketAssetService.getMyTicketDetail(ticketAssetId)));
    }

    @GetMapping("/{ticketAssetId}/resale-eligibility")
    public ResponseEntity<BaseResponse<ResaleEligibilityResponse>> getResaleEligibility(
            @PathVariable Long ticketAssetId
    ) {
        return ResponseEntity.ok(BaseResponse.ok("Fetched resale eligibility successfully",
                ticketAssetService.getResaleEligibility(ticketAssetId)));
    }
}
