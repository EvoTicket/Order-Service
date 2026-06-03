package com.capstone.orderservice.controller;

import com.capstone.orderservice.dto.BaseResponse;
import com.capstone.orderservice.dto.response.AdminHealthDashboardResponse;
import com.capstone.orderservice.service.AdminHealthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/health")
@RequiredArgsConstructor
@Tag(name = "Admin Health Monitor", description = "Các endpoint để giám sát sức khỏe hệ thống cho Admin")
public class AdminHealthController {

    private final AdminHealthService adminHealthService;

    @Operation(summary = "Lấy dữ liệu sức khỏe hệ thống", description = "Trả về trạng thái các microservice từ Eureka cùng các thông số hàng đợi và lỗi")
    @GetMapping
    public ResponseEntity<BaseResponse<AdminHealthDashboardResponse>> getHealthDashboard(
            @RequestParam(defaultValue = "1h") String timeRange) {
        
        AdminHealthDashboardResponse response = adminHealthService.getHealthDashboard(timeRange);
        return ResponseEntity.ok(BaseResponse.ok("Lấy dữ liệu sức khỏe hệ thống thành công", response));
    }
}
