package com.capstone.orderservice.controller;

import com.capstone.orderservice.dto.BaseResponse;
import com.capstone.orderservice.dto.response.AdminSupportDashboardResponse;
import com.capstone.orderservice.service.AdminSupportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/support")
@RequiredArgsConstructor
@Tag(name = "Admin Support Dashboard", description = "Các endpoint để quản lý thông tin support cho Admin")
public class AdminSupportController {

    private final AdminSupportService adminSupportService;

    @Operation(summary = "Lấy thông tin dashboard support cho Admin", description = "Trả về stats và danh sách phân trang tương ứng với tab (transactions, tickets, cases)")
    @GetMapping
    public ResponseEntity<BaseResponse<AdminSupportDashboardResponse>> getSupportDashboard(
            @RequestParam(defaultValue = "transactions") String tab,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        
        AdminSupportDashboardResponse response = adminSupportService.getSupportDashboard(tab, search, page, size);
        return ResponseEntity.ok(BaseResponse.ok("Lấy dữ liệu support thành công", response));
    }
}
