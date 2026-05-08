package com.capstone.orderservice.controller;

import com.capstone.orderservice.dto.BasePageResponse;
import com.capstone.orderservice.dto.BaseResponse;
import com.capstone.orderservice.dto.request.CreateResaleListingRequest;
import com.capstone.orderservice.dto.request.ResaleCheckoutRequest;
import com.capstone.orderservice.dto.request.ResaleQuoteRequest;
import com.capstone.orderservice.dto.response.ResaleCheckoutResponse;
import com.capstone.orderservice.dto.response.ResaleListingResponse;
import com.capstone.orderservice.dto.response.ResalePaymentStatusResponse;
import com.capstone.orderservice.dto.response.ResaleQuoteResponse;
import com.capstone.orderservice.enums.ResaleSortOption;
import com.capstone.orderservice.service.ResaleCheckoutFacade;
import com.capstone.orderservice.service.ResaleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/resale")
@RequiredArgsConstructor
@Tag(name = "Thị trường Chuyển nhượng", description = "Các endpoint để đăng bán và mua lại vé (Resale)")
public class ResaleController {
        private final ResaleService resaleService;
        private final ResaleCheckoutFacade resaleCheckoutFacade;

        @Operation(summary = "Tính toán báo giá chuyển nhượng", description = "Tính toán các loại phí và số tiền nhận được khi đăng bán vé.")
        @PostMapping("/quote")
        public ResponseEntity<BaseResponse<ResaleQuoteResponse>> quote(@Valid @RequestBody ResaleQuoteRequest request) {
                return ResponseEntity.ok(
                                BaseResponse.ok("Resale quote calculated successfully", resaleService.quote(request)));
        }

        @Operation(summary = "Lấy danh sách vé đang đăng bán", description = "Trả về danh sách các vé đang được rao bán trên thị trường với các bộ lọc.")
        @GetMapping("/listings")
        public ResponseEntity<BaseResponse<BasePageResponse<ResaleListingResponse>>> getActiveListings(
                        @RequestParam(required = false) Long eventId,
                        @RequestParam(required = false) Long ticketTypeId,
                        @RequestParam(required = false) BigDecimal minPrice,
                        @RequestParam(required = false) BigDecimal maxPrice,
                        @RequestParam(required = false) String listingCode,
                        @RequestParam(required = false) String category,
                        @RequestParam(required = false) Integer provinceCode,
                        @RequestParam(required = false) String keyword,
                        @RequestParam(required = false) java.time.LocalDateTime startTime,
                        @RequestParam(required = false) java.time.LocalDateTime endTime,
                        @RequestParam(required = false) ResaleSortOption sortBy,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "20") int size) {
                Pageable pageable = PageRequest.of(page, size);
                Page<ResaleListingResponse> listings = resaleService.getActiveListings(
                                eventId,
                                ticketTypeId,
                                minPrice,
                                maxPrice,
                                listingCode,
                                category,
                                provinceCode,
                                keyword,
                                startTime,
                                endTime,
                                sortBy,
                                pageable);

                return ResponseEntity.ok(BaseResponse.ok("Fetched resale listings successfully",
                                BasePageResponse.fromPage(listings)));
        }

        @Operation(summary = "Xem chi tiết tin đăng bán", description = "Lấy thông tin chi tiết của một tin đăng bán vé cụ thể qua mã tin.")
        @GetMapping("/listings/{listingCode}")
        public ResponseEntity<BaseResponse<ResaleListingResponse>> getActiveListingDetail(
                        @PathVariable String listingCode) {
                return ResponseEntity.ok(BaseResponse.ok("Fetched resale listing successfully",
                                resaleService.getActiveListingDetail(listingCode)));
        }

        @Operation(summary = "Đăng bán vé", description = "Tạo một tin đăng bán vé mới lên thị trường chuyển nhượng.")
        @PostMapping("/listings")
        public ResponseEntity<BaseResponse<ResaleListingResponse>> createListing(
                        @Valid @RequestBody CreateResaleListingRequest request) {
                return ResponseEntity.ok(BaseResponse.created("Resale listing created successfully",
                                resaleService.createListing(request)));
        }

        @Operation(summary = "Hủy tin đăng bán", description = "Gỡ tin đăng bán vé khỏi thị trường.")
        @PostMapping("/listings/{listingCode}/cancel")
        public ResponseEntity<BaseResponse<ResaleListingResponse>> cancelListing(@PathVariable String listingCode) {
                return ResponseEntity.ok(BaseResponse.ok("Resale listing cancelled successfully",
                                resaleService.cancelListing(listingCode)));
        }

        @Operation(summary = "Thanh toán mua lại vé", description = "Tạo đơn hàng mua lại vé từ thị trường chuyển nhượng.")
        @PostMapping("/listings/{listingCode}/checkout")
        public ResponseEntity<BaseResponse<ResaleCheckoutResponse>> checkout(
                        @PathVariable String listingCode,
                        @Valid @RequestBody ResaleCheckoutRequest request) {
                return ResponseEntity.ok(BaseResponse.created("Resale checkout order created successfully",
                                resaleCheckoutFacade.checkout(listingCode, request)));
        }

        @Operation(summary = "Kiểm tra trạng thái thanh toán resale", description = "Kiểm tra xem đơn hàng mua lại vé đã thanh toán thành công hay chưa.")
        @GetMapping("/orders/{orderCode}/payment-status")
        public ResponseEntity<BaseResponse<ResalePaymentStatusResponse>> getPaymentStatus(
                        @PathVariable String orderCode) {
                return ResponseEntity.ok(BaseResponse.ok("Fetched resale payment status successfully",
                                resaleService.getPaymentStatus(orderCode)));
        }

        @Operation(summary = "Tiếp tục thanh toán resale", description = "Tạo lại link thanh toán cho một đơn hàng mua lại vé đang chờ.")
        @PostMapping("/orders/{orderCode}/continue-payment")
        public ResponseEntity<BaseResponse<ResalePaymentStatusResponse>> continuePayment(
                        @PathVariable String orderCode) {
                return ResponseEntity.ok(BaseResponse.ok("Resale payment link created successfully",
                                resaleCheckoutFacade.continuePayment(orderCode)));
        }
}
