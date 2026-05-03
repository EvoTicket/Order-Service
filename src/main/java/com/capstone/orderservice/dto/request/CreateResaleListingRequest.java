package com.capstone.orderservice.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateResaleListingRequest {
    @NotNull(message = "Ticket asset id is required")
    private Long ticketAssetId;

    @NotNull(message = "Listing price is required")
    private BigDecimal listingPrice;
}
