package com.capstone.orderservice.controller;

import com.capstone.orderservice.dto.BaseResponse;
import com.capstone.orderservice.dto.response.AdminResaleDashboardResponse;
import com.capstone.orderservice.service.AdminResaleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/resale")
@RequiredArgsConstructor
@Tag(name = "Admin Resale Dashboard", description = "Các endpoint để quản lý thông tin resale cho Admin")
public class AdminResaleController {

    private final AdminResaleService adminResaleService;

    @Operation(summary = "Lấy thông tin dashboard resale cho Admin", description = "Trả về stats, volume charts, compliance, spikes, và danh sách phân trang các listings review/disputes")
    @GetMapping
    public ResponseEntity<BaseResponse<AdminResaleDashboardResponse>> getResaleDashboard(
            @RequestParam(defaultValue = "monitoring") String tab,
            @RequestParam(defaultValue = "Tất cả") String statusFilter,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        
        AdminResaleDashboardResponse response = adminResaleService.getResaleDashboard(tab, statusFilter, search, page, size);
        return ResponseEntity.ok(BaseResponse.ok("Lấy dữ liệu resale thành công", response));
    }
}
