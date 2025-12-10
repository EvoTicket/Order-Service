package com.capstone.orderservice.client;

import com.capstone.orderservice.config.FeignClientConfig;
import com.capstone.orderservice.dto.BaseResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(
        name = "inventory-service",
        configuration = FeignClientConfig.class
)
public interface InventoryFeignClient {
    @GetMapping("/api/ticket-types/{ticketTypeId}")
    BaseResponse<TicketTypeResponse> getTicketTypeById(@PathVariable("ticketTypeId") Long ticketTypeId);
}
