package com.capstone.orderservice.controller;

import com.capstone.orderservice.dto.BasePageResponse;
import com.capstone.orderservice.dto.BaseResponse;
import com.capstone.orderservice.dto.request.CreateOrderRequest;
import com.capstone.orderservice.dto.response.OrderResponse;
import com.capstone.orderservice.enums.OrderStatus;
import com.capstone.orderservice.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {
    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<BaseResponse<OrderResponse>> createOrder(
            @Valid @RequestBody CreateOrderRequest request) {
        OrderResponse response = orderService.createOrder(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(BaseResponse.created("Tạo đơn hàng thành công", response));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<BaseResponse<OrderResponse>> getOrderById(
            @PathVariable Long orderId) {
        OrderResponse response = orderService.getOrderByOrderId(orderId);
        return ResponseEntity.ok(BaseResponse.ok("Lấy thông tin đơn hàng thành công", response));
    }


    @GetMapping("/user/{userId}")
    public ResponseEntity<BaseResponse<BasePageResponse<OrderResponse>>> getOrdersByUserId(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String direction) {

        Sort.Direction sortDirection = direction.equalsIgnoreCase("ASC")
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sortBy));

        Page<OrderResponse> orderPage = orderService.getOrdersByUserId(pageable);
        BasePageResponse<OrderResponse> pageResponse = BasePageResponse.fromPage(orderPage);

        return ResponseEntity.ok(BaseResponse.ok("Lấy danh sách đơn hàng thành công", pageResponse));
    }

    @GetMapping
    public ResponseEntity<BaseResponse<BasePageResponse<OrderResponse>>> getAllOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String direction) {

        Sort.Direction sortDirection = direction.equalsIgnoreCase("ASC")
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sortBy));

        Page<OrderResponse> orderPage = orderService.getAllOrders(pageable);
        BasePageResponse<OrderResponse> pageResponse = BasePageResponse.fromPage(orderPage);

        return ResponseEntity.ok(BaseResponse.ok("Lấy danh sách đơn hàng thành công", pageResponse));
    }

    @GetMapping("/status")
    public ResponseEntity<BaseResponse<BasePageResponse<OrderResponse>>> getOrdersByStatus(
            @RequestParam OrderStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<OrderResponse> orderPage = orderService.getOrdersByStatus(status, pageable);
        BasePageResponse<OrderResponse> pageResponse = BasePageResponse.fromPage(orderPage);

        return ResponseEntity.ok(BaseResponse.ok("Lấy danh sách đơn hàng thành công", pageResponse));
    }

    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<BaseResponse<Void>> cancelOrder(@PathVariable Long orderId) {
        orderService.cancelOrder(orderId);
        return ResponseEntity.ok(BaseResponse.ok("Hủy đơn hàng thành công", null));
    }
}