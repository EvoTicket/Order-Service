package com.capstone.orderservice.controller;

import com.capstone.orderservice.dto.BasePageResponse;
import com.capstone.orderservice.dto.BaseResponse;
import com.capstone.orderservice.dto.request.ApplyVoucherRequest;
import com.capstone.orderservice.dto.request.CreateVoucherRequest;
import com.capstone.orderservice.dto.request.UpdateVoucherRequest;
import com.capstone.orderservice.dto.response.VoucherResponse;
import com.capstone.orderservice.service.VoucherService;
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
@RequestMapping("/api/v1/vouchers")
@RequiredArgsConstructor
public class VoucherController {
    private final VoucherService voucherService;

    @PostMapping
    public ResponseEntity<BaseResponse<VoucherResponse>> createVoucher(
            @Valid @RequestBody CreateVoucherRequest request) {
        VoucherResponse response = voucherService.createVoucher(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(BaseResponse.created("Tạo voucher thành công", response));
    }

    @GetMapping("/{voucherId}")
    public ResponseEntity<BaseResponse<VoucherResponse>> getVoucher(
            @PathVariable Long voucherId) {
        VoucherResponse response = voucherService.getVoucherByVoucherId(voucherId);
        return ResponseEntity.ok(BaseResponse.ok("Lấy thông tin voucher thành công", response));
    }

    @GetMapping
    public ResponseEntity<BaseResponse<BasePageResponse<VoucherResponse>>> getAllVouchers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String direction) {

        Sort.Direction sortDirection = direction.equalsIgnoreCase("ASC")
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sortBy));

        Page<VoucherResponse> voucherPage = voucherService.getAllVouchers(pageable);
        BasePageResponse<VoucherResponse> pageResponse = BasePageResponse.fromPage(voucherPage);

        return ResponseEntity.ok(BaseResponse.ok("Lấy danh sách voucher thành công", pageResponse));
    }

    @GetMapping("/active")
    public ResponseEntity<BaseResponse<BasePageResponse<VoucherResponse>>> getActiveVouchers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<VoucherResponse> voucherPage = voucherService.getActiveVouchers(pageable);
        BasePageResponse<VoucherResponse> pageResponse = BasePageResponse.fromPage(voucherPage);

        return ResponseEntity.ok(BaseResponse.ok("Lấy danh sách voucher khả dụng thành công", pageResponse));
    }

    @PutMapping("/{voucherId}")
    public ResponseEntity<BaseResponse<VoucherResponse>> updateVoucher(
            @PathVariable Long voucherId,
            @Valid @RequestBody UpdateVoucherRequest request) {
        VoucherResponse response = voucherService.updateVoucher(voucherId, request);
        return ResponseEntity.ok(BaseResponse.ok("Cập nhật voucher thành công", response));
    }

    @DeleteMapping("/{voucherId}")
    public ResponseEntity<BaseResponse<Boolean>> deleteVoucher(@PathVariable Long voucherId) {
        return ResponseEntity.ok(BaseResponse.ok("Xóa voucher thành công",  voucherService.deleteVoucher(voucherId)));
    }
}