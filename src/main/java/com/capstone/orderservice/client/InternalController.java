package com.capstone.orderservice.client;

import com.capstone.orderservice.dto.BaseResponse;
import com.capstone.orderservice.dto.response.EventVolumeDto;
import com.capstone.orderservice.dto.response.OrganizerOrdersStatsResponse;
import com.capstone.orderservice.service.OrderService;
import com.capstone.orderservice.service.TicketAssetService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/internal")
@RequiredArgsConstructor
public class InternalController {
    private final OrderService orderService;
    private final TicketAssetService ticketAssetService;

    @GetMapping("/orders/detail")
    public ResponseEntity<BaseResponse<OrderInternalResponse>> getOrderDetail(
            @RequestParam String orderCode) {
        OrderInternalResponse response = orderService.getOrdersDetail(orderCode);
        return ResponseEntity.ok(BaseResponse.ok("Lấy đơn hàng thành công", response));
    }

    @PostMapping("/orders/volume")
    public ResponseEntity<Map<Long, EventVolumeDto>> getVolumeForEvents(
            @RequestBody List<Long> eventIds) {
        Map<Long, EventVolumeDto> volumeMap = orderService.getVolumeForEvents(eventIds);
        return ResponseEntity.ok(volumeMap);
    }

    @GetMapping("/orders/user/{userId}/purchased-events")
    public ResponseEntity<List<Long>> getPurchasedEventIds(
            @PathVariable Long userId) {
        return ResponseEntity.ok(orderService.getPurchasedEventIdsByUserId(userId));
    }

    @GetMapping("/orders/events/revenue")
    public ResponseEntity<Map<Long, BigDecimal>> getRevenueForEvent(
            @RequestParam List<Long> eventIds) {
        return ResponseEntity.ok(orderService.getRevenueMap(eventIds));
    }

    @GetMapping("/orders/platform-stats")
    public ResponseEntity<com.capstone.orderservice.dto.response.PlatformStatsResponse> getPlatformStats(
            @RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(orderService.getPlatformStats(days));
    }

    @GetMapping("/orders/organizer-stats")
    public ResponseEntity<OrganizerOrdersStatsResponse> getOrganizerStats(
            @RequestParam List<Long> eventIds,
            @RequestParam int days) {
        return ResponseEntity.ok(orderService.getOrganizerStats(eventIds, days));
    }

    @GetMapping("/tickets/{ticketAssetId}/verify-ownership")
    public ResponseEntity<Boolean> verifyOwnership(
            @PathVariable Long ticketAssetId,
            @RequestParam Long currentOwnerId) {
        boolean isOwner = ticketAssetService.verifyTicketOwnership(ticketAssetId, currentOwnerId);
        return ResponseEntity.ok(isOwner);
    }
}
