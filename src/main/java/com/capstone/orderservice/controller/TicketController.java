package com.capstone.orderservice.controller;

import com.capstone.orderservice.dto.BaseResponse;
import com.capstone.orderservice.dto.response.MyTicketGroupResponse;
import com.capstone.orderservice.dto.response.ResaleEligibilityResponse;
import com.capstone.orderservice.dto.response.RichTicketProvenanceResponse;
import com.capstone.orderservice.dto.response.TicketAssetResponse;
import com.capstone.orderservice.dto.response.TicketProvenanceResponse;
import com.capstone.orderservice.service.TicketAssetService;
import com.capstone.orderservice.service.TicketProvenanceService;
import com.capstone.orderservice.scheduler.CheckInSyncScheduler;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.capstone.orderservice.dto.request.WithdrawTicketRequest;
import com.capstone.orderservice.dto.response.WithdrawResponse;
import com.capstone.orderservice.exception.AppException;
import java.util.Map;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/tickets")
@RequiredArgsConstructor
@Tag(name = "Quản lý Vé (User Assets)", description = "Các endpoint để xem vé cá nhân và thông tin blockchain của vé")
public class TicketController {
    private final TicketAssetService ticketAssetService;
    private final TicketProvenanceService ticketProvenanceService;
    private final CheckInSyncScheduler checkInSyncScheduler;

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

    @Operation(summary = "Rút vé về ví cá nhân", description = "Gửi yêu cầu rút vé NFT từ ví custodial sang ví cá nhân.")
    @PostMapping("/withdraw")
    public ResponseEntity<?> withdrawTicket(@RequestBody WithdrawTicketRequest request) {
        try {
            WithdrawResponse response = ticketAssetService.withdrawTicket(request.getTokenId(), request.getPersonalWallet());
            return ResponseEntity.ok(response);
        } catch (AppException e) {
            return ResponseEntity.status(e.getErrorCode().getStatus())
                    .body(Map.of("error", e.getCustomMessage() != null ? e.getCustomMessage() : e.getErrorCode().getDefaultMessage()));
        }
    }

    @Operation(summary = "Đồng bộ vé đã check-in lên blockchain thủ công", description = "Chạy thủ công tác vụ đồng bộ trạng thái check-in của vé lên blockchain.")
    @PostMapping("/sync-blockchain")
    public ResponseEntity<BaseResponse<String>> syncCheckedInTickets() {
        checkInSyncScheduler.syncCheckedInTicketsToBlockchain();
        return ResponseEntity.ok(BaseResponse.ok("Đồng bộ thành công", "SUCCESS"));
    }
}
