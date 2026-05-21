package com.capstone.orderservice.service;

import com.capstone.orderservice.client.*;
import com.capstone.orderservice.exception.AppException;
import com.capstone.orderservice.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResalePayoutService {
    private final PaymentFeignClient paymentFeignClient;
    private final InventoryFeignClient inventoryFeignClient;
    private final IamFeignClient iamFeignClient;

    public void processResalePayout(BigDecimal amount, Long sellerId) {
        UserBankAccountResponse bankInfo = iamFeignClient.getMyBankInfo(sellerId).getData();
        if(bankInfo == null) {
            log.warn("Seller with ID {} does not have bank information for payout", sellerId);
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "Seller does not have bank information for payout");
        }

        ResalePayoutRequest request = ResalePayoutRequest.builder()
                .amount(amount)
                .binCode(inventoryFeignClient.getBinCodeFromBankCode(bankInfo.getBankCode()).getData())
                .bankAccountNumber(bankInfo.getBankAccountNumber())
                .build();
        paymentFeignClient.processResalePayout(request);
    }
}
