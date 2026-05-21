package com.capstone.orderservice.client;

import com.capstone.orderservice.config.FeignClientConfig;
import com.capstone.orderservice.dto.BaseResponse;
import com.capstone.orderservice.dto.response.PaymentLinkResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    @PostMapping("/payment/create")
    BaseResponse<PaymentLinkResponse> createPaymentLink(@RequestBody OrderInternalResponse order);

    @PostMapping("/payment/resale/pay-out")
    BaseResponse<Boolean> processResalePayout(@RequestBody ResalePayoutRequest request);
}

