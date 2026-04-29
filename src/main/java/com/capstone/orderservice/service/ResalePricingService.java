package com.capstone.orderservice.service;

import lombok.Builder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class ResalePricingService {
    private static final int MONEY_SCALE = 2;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;
    private static final BigDecimal PRICE_CAP_MULTIPLIER = new BigDecimal("1.10");
    private static final BigDecimal PLATFORM_FEE_RATE = new BigDecimal("0.02");
    private static final BigDecimal ORGANIZER_ROYALTY_RATE = BigDecimal.ZERO;

    public ResalePricing calculate(BigDecimal originalPrice, BigDecimal listingPrice) {
        BigDecimal safeOriginalPrice = money(originalPrice);
        BigDecimal safeListingPrice = money(listingPrice);
        BigDecimal priceCap = money(safeOriginalPrice.multiply(PRICE_CAP_MULTIPLIER));
        BigDecimal platformFeeAmount = money(safeListingPrice.multiply(PLATFORM_FEE_RATE));
        BigDecimal organizerRoyaltyAmount = money(safeListingPrice.multiply(ORGANIZER_ROYALTY_RATE));
        BigDecimal sellerPayoutAmount = money(safeListingPrice.subtract(platformFeeAmount).subtract(organizerRoyaltyAmount));

        return ResalePricing.builder()
                .originalPrice(safeOriginalPrice)
                .listingPrice(safeListingPrice)
                .priceCap(priceCap)
                .platformFeeRate(PLATFORM_FEE_RATE)
                .organizerRoyaltyRate(ORGANIZER_ROYALTY_RATE)
                .platformFeeAmount(platformFeeAmount)
                .organizerRoyaltyAmount(organizerRoyaltyAmount)
                .sellerPayoutAmount(sellerPayoutAmount)
                .build();
    }

    private BigDecimal money(BigDecimal value) {
        BigDecimal safeValue = value != null ? value : BigDecimal.ZERO;
        return safeValue.setScale(MONEY_SCALE, ROUNDING_MODE);
    }

    @Builder
    public record ResalePricing(
            BigDecimal originalPrice,
            BigDecimal listingPrice,
            BigDecimal priceCap,
            BigDecimal platformFeeRate,
            BigDecimal organizerRoyaltyRate,
            BigDecimal platformFeeAmount,
            BigDecimal organizerRoyaltyAmount,
            BigDecimal sellerPayoutAmount
    ) {
    }
}
