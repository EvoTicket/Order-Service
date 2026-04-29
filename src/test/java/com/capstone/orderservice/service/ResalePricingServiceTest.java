package com.capstone.orderservice.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ResalePricingServiceTest {
    private final ResalePricingService resalePricingService = new ResalePricingService();

    @Test
    void calculateUsesExpectedRatesAndRounding() {
        ResalePricingService.ResalePricing pricing = resalePricingService.calculate(
                new BigDecimal("100000"),
                new BigDecimal("105000")
        );

        assertThat(pricing.priceCap()).isEqualByComparingTo("110000.00");
        assertThat(pricing.platformFeeRate()).isEqualByComparingTo("0.02");
        assertThat(pricing.organizerRoyaltyRate()).isEqualByComparingTo("0");
        assertThat(pricing.platformFeeAmount()).isEqualByComparingTo("2100.00");
        assertThat(pricing.organizerRoyaltyAmount()).isEqualByComparingTo("0.00");
        assertThat(pricing.sellerPayoutAmount()).isEqualByComparingTo("102900.00");
    }
}
