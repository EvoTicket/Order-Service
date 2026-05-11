package com.capstone.orderservice.controller;

import com.capstone.orderservice.dto.BaseResponse;
import com.capstone.orderservice.dto.response.MyTicketGroupResponse;
import com.capstone.orderservice.dto.response.ResaleEligibilityResponse;
import com.capstone.orderservice.dto.response.RichTicketProvenanceResponse;
import com.capstone.orderservice.dto.response.TicketAssetResponse;
import com.capstone.orderservice.dto.response.TicketProvenanceResponse;
import com.capstone.orderservice.service.TicketAssetService;
import com.capstone.orderservice.service.TicketProvenanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/tickets")
@RequiredArgsConstructor
@Tag(name = "Quản lý Vé (User Assets)", description = "Các endpoint để xem vé cá nhân và thông tin blockchain của vé")
public class TicketController {
    private final TicketAssetService ticketAssetService;
    private final TicketProvenanceService ticketProvenanceService;

    @Operation(summary = "Lấy danh sách vé của tôi", description = "Trả về danh sách tất cả các vé mà người dùng hiện tại đang sở hữu.")
    @GetMapping("/me")
    public ResponseEntity<BaseResponse<java.util.List<MyTicketGroupResponse>>> getMyTickets() {
        return ResponseEntity.ok(BaseResponse.ok("Fetched my tickets successfully", ticketAssetService.getMyTickets()));
    }

    @Operation(summary = "Xem chi tiết vé", description = "Lấy thông tin chi tiết của một vé cụ thể (bao gồm mã QR, sự kiện).")
    @GetMapping("/{ticketAssetId}")
    public ResponseEntity<BaseResponse<TicketAssetResponse>> getTicketDetail(@PathVariable Long ticketAssetId) {
        return ResponseEntity.ok(BaseResponse.ok("Fetched ticket detail successfully",
                ticketAssetService.getMyTicketDetail(ticketAssetId)));
    }

    @Operation(summary = "Kiểm tra điều kiện đăng bán", description = "Kiểm tra xem một vé có đủ điều kiện để đăng bán lại hay không.")
    @GetMapping("/{ticketAssetId}/resale-eligibility")
    public ResponseEntity<BaseResponse<ResaleEligibilityResponse>> getResaleEligibility(
            @PathVariable Long ticketAssetId
    ) {
        return ResponseEntity.ok(BaseResponse.ok("Fetched resale eligibility successfully",
                ticketAssetService.getResaleEligibility(ticketAssetId)));
    }
    @Operation(summary = "Xem lịch sử blockchain của vé (Provenance)", description = "Truy xuất lịch sử sở hữu và các giao dịch on-chain của vé.")
    @GetMapping("/{ticketAssetId}/provenance")
    public ResponseEntity<BaseResponse<RichTicketProvenanceResponse>> getTicketProvenance(
            @PathVariable Long ticketAssetId
    ) {
        return ResponseEntity.ok(BaseResponse.ok("Fetched ticket provenance successfully",
                ticketProvenanceService.getProvenanceForMyTicket(ticketAssetId)));
    }
}
