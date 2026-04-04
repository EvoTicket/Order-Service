package com.capstone.orderservice.client;

import com.capstone.orderservice.dto.BaseResponse;
import com.capstone.orderservice.dto.response.EventVolumeDto;
import com.capstone.orderservice.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/internal")
@RequiredArgsConstructor
public class InternalController {
    private final OrderService orderService;

    @GetMapping("/orders/detail")
    public ResponseEntity<BaseResponse<OrderInternalResponse>> getOrderDetail(
            @RequestParam String orderCode
    ){
        OrderInternalResponse response = orderService.getOrdersDetail(orderCode);
        return ResponseEntity.ok(BaseResponse.ok("Lấy đơn hàng thành công", response));
    }

    @PostMapping("/orders/volume")
    public ResponseEntity<Map<Long, EventVolumeDto>> getVolumeForEvents(
            @RequestBody List<Long> eventIds
    ){
        Map<Long, EventVolumeDto> volumeMap = orderService.getVolumeForEvents(eventIds);
        return ResponseEntity.ok(volumeMap);
    }

    @GetMapping("/orders/user/{userId}/purchased-events")
    public ResponseEntity<List<Long>> getPurchasedEventIds(
            @PathVariable Long userId
    ){
        return ResponseEntity.ok(orderService.getPurchasedEventIdsByUserId(userId));
    }
}
