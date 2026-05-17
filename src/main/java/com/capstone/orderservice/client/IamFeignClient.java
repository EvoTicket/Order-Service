package com.capstone.orderservice.client;

import com.capstone.orderservice.config.FeignClientConfig;
import com.capstone.orderservice.dto.BaseResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(
        name = "iam-service",
        path = "/api/internal",
        configuration = FeignClientConfig.class
)
public interface IamFeignClient {
    @GetMapping("/users/{userId}/bank-info")
    BaseResponse<UserBankAccountResponse> getMyBankInfo(@PathVariable Long userId);
}
