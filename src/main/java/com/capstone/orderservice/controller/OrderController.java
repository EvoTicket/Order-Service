package com.capstone.orderservice.controller;

import com.capstone.orderservice.dto.BasePageResponse;
import com.capstone.orderservice.dto.BaseResponse;
import com.capstone.orderservice.dto.request.CreateOrderRequest;
import com.capstone.orderservice.dto.response.OrderResponse;
import com.capstone.orderservice.dto.response.PaymentLinkResponse;
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
import org.springframework.beans.factory.annotation.Value;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Tag(name = "Quản lý Đơn hàng", description = "Các endpoint để tạo, quản lý và tra cứu đơn hàng")
public class OrderController {
    private final OrderService orderService;
    
    @Value("${front-end.domain}")
    private String frontendDomain;

    @Operation(summary = "Tạo đơn hàng mới", description = "Tạo một đơn hàng mới cho vé sự kiện và trả về link thanh toán.")
    @PostMapping
    public ResponseEntity<BaseResponse<PaymentLinkResponse>> createOrder(
            @Valid @RequestBody CreateOrderRequest request) {
        PaymentLinkResponse response = orderService.createOrder(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(BaseResponse.created("Tạo đơn hàng thành công", response));
    }

    @Operation(summary = "Lấy thông tin đơn hàng theo ID", description = "Trả về thông tin chi tiết của một đơn hàng cụ thể dựa trên ID.")
    @GetMapping("/{orderId}")
    public ResponseEntity<BaseResponse<OrderResponse>> getOrderById(
            @PathVariable Long orderId) {
        OrderResponse response = orderService.getOrderByOrderId(orderId);
        return ResponseEntity.ok(BaseResponse.ok("Lấy thông tin đơn hàng thành công", response));
    }

    @Operation(summary = "Lấy đơn hàng của người dùng hiện tại", description = "Trả về danh sách các đơn hàng đã mua bởi người dùng đang đăng nhập.")
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

    @Operation(summary = "Lấy tất cả đơn hàng (Admin)", description = "Trả về danh sách tất cả các đơn hàng trong hệ thống (phân trang).")
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

    @Operation(summary = "Lấy đơn hàng theo trạng thái", description = "Trả về danh sách các đơn hàng lọc theo trạng thái (PENDING, CONFIRMED, v.v.).")
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

    @Operation(summary = "Hủy đơn hàng", description = "Hủy một đơn hàng đang ở trạng thái chờ thanh toán.")
    @PostMapping("/{orderCode}/cancel")
    public ResponseEntity<BaseResponse<Boolean>> cancelOrder(@PathVariable String orderCode) {
        orderService.cancelOrder(orderCode);
        return ResponseEntity.ok(BaseResponse.ok("Hủy đơn hàng thành công", true));
    }

    @Operation(summary = "Callback khi hủy thanh toán", description = "Xử lý hủy đơn hàng và redirect về trang kết quả ở frontend.")
    @GetMapping("/cancel-callback")
    public void handleCancelCallback(
            @RequestParam String orderCode,
            @RequestParam Long eventId,
            @RequestParam String locale,
            HttpServletResponse response) throws IOException {
        try {
            orderService.cancelOrder(orderCode);
        } catch (Exception e) {
            // Log error and still redirect to ensure user doesn't get stuck
        }
        String redirectUrl = frontendDomain + "/" + locale + "/user/events/" + eventId + "/payment/result?status=CANCELLED&orderCode=" + orderCode;
        response.sendRedirect(redirectUrl);
    }
}