package com.capstone.orderservice.client;

import com.capstone.orderservice.dto.BaseResponse;
import com.capstone.orderservice.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal")
@RequiredArgsConstructor
public class InternalController {
    private final OrderService orderService;

    @GetMapping("/orders/detail")
    public ResponseEntity<BaseResponse<OrderInternalResponse>> getOrderDetail(
            @RequestParam Long orderId
    ){
        OrderInternalResponse response = orderService.getOrdersDetail(orderId);
        return ResponseEntity.ok(BaseResponse.ok("Lấy đơn hàng thành công", response));
    }
}
