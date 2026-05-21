package com.capstone.orderservice.client;

import com.capstone.orderservice.config.FeignClientConfig;
import com.capstone.orderservice.dto.BaseResponse;
import com.capstone.orderservice.dto.request.OrderItemRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@FeignClient(
        name = "inventory-service",
        path = "/api/internal",
        configuration = FeignClientConfig.class
)
public interface InventoryFeignClient {
    @PostMapping("/ticket-types/tickets")
    BaseResponse<ListTicketTypesInternalResponse> getTicketTypes(@RequestBody List<OrderItemRequest> listItems);

    @PostMapping("/ticket-types/release")
    BaseResponse<Boolean> releaseTickets(@RequestBody List<OrderItemRequest> listItems);

    @GetMapping("/event/{ticketTypeId}")
    BaseResponse<EventDetailInternalResponse> getEventDetailsByTicketTypeId(@PathVariable Long ticketTypeId);

    @PostMapping("/ticket-types/details")
    BaseResponse<List<TicketTypeInternalResponse>> getTicketDetails(@RequestBody List<Long> ticketTypeIds);

    @GetMapping("/event/{eventId}/allow-resale")
    boolean getAllowResale(@PathVariable Long eventId);

    @GetMapping("/bank/bin-code")
    BinCodeDto getBinCodeFromBankCode(@RequestParam String bankCode);
}
