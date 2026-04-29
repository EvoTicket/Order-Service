package com.capstone.orderservice.controller;

import com.capstone.orderservice.dto.BasePageResponse;
import com.capstone.orderservice.dto.BaseResponse;
import com.capstone.orderservice.dto.request.CreateResaleListingRequest;
import com.capstone.orderservice.dto.request.ResaleCheckoutRequest;
import com.capstone.orderservice.dto.request.ResaleQuoteRequest;
import com.capstone.orderservice.dto.response.ResaleCheckoutResponse;
import com.capstone.orderservice.dto.response.ResaleListingResponse;
import com.capstone.orderservice.dto.response.ResaleQuoteResponse;
import com.capstone.orderservice.service.ResaleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/resale")
@RequiredArgsConstructor
public class ResaleController {
    private final ResaleService resaleService;

    @PostMapping("/quote")
    public ResponseEntity<BaseResponse<ResaleQuoteResponse>> quote(@Valid @RequestBody ResaleQuoteRequest request) {
        return ResponseEntity.ok(BaseResponse.ok("Resale quote calculated successfully", resaleService.quote(request)));
    }

    @GetMapping("/listings")
    public ResponseEntity<BaseResponse<BasePageResponse<ResaleListingResponse>>> getActiveListings(
            @RequestParam(required = false) Long eventId,
            @RequestParam(required = false) Long ticketTypeId,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<ResaleListingResponse> listings = resaleService.getActiveListings(
                eventId,
                ticketTypeId,
                minPrice,
                maxPrice,
                pageable
        );

        return ResponseEntity.ok(BaseResponse.ok("Fetched resale listings successfully",
                BasePageResponse.fromPage(listings)));
    }

    @GetMapping("/listings/{listingCode}")
    public ResponseEntity<BaseResponse<ResaleListingResponse>> getActiveListingDetail(@PathVariable String listingCode) {
        return ResponseEntity.ok(BaseResponse.ok("Fetched resale listing successfully",
                resaleService.getActiveListingDetail(listingCode)));
    }

    @PostMapping("/listings")
    public ResponseEntity<BaseResponse<ResaleListingResponse>> createListing(
            @Valid @RequestBody CreateResaleListingRequest request
    ) {
        return ResponseEntity.ok(BaseResponse.created("Resale listing created successfully",
                resaleService.createListing(request)));
    }

    @PostMapping("/listings/{listingCode}/cancel")
    public ResponseEntity<BaseResponse<ResaleListingResponse>> cancelListing(@PathVariable String listingCode) {
        return ResponseEntity.ok(BaseResponse.ok("Resale listing cancelled successfully",
                resaleService.cancelListing(listingCode)));
    }

    @PostMapping("/listings/{listingCode}/checkout")
    public ResponseEntity<BaseResponse<ResaleCheckoutResponse>> checkout(
            @PathVariable String listingCode,
            @Valid @RequestBody ResaleCheckoutRequest request
    ) {
        return ResponseEntity.ok(BaseResponse.created("Resale checkout order created successfully",
                resaleService.checkout(listingCode, request)));
    }
}
