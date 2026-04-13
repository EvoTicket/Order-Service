package com.capstone.orderservice.client;

import com.capstone.orderservice.config.FeignClientConfig;
import com.capstone.orderservice.dto.BaseResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

@FeignClient(
        name = "payment-service",
        path = "/api/internal",
        configuration = FeignClientConfig.class
)
public interface PaymentFeignClient {
    @PostMapping("/cancel-payment/{orderCode}")
    BaseResponse<Boolean> cancelPayment(@PathVariable String orderCode);

    @GetMapping("/payment/{orderCode}")
    BaseResponse<PaymentTransactionResponse> getPaymentInfo(@PathVariable String orderCode);
}

