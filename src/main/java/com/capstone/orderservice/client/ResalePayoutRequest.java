package com.capstone.orderservice.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResalePayoutRequest {
    private BigDecimal amount;
    private String binCode;
    private String bankAccountNumber;
}
