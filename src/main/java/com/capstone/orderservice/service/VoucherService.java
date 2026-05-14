package com.capstone.orderservice.service;

import com.capstone.orderservice.dto.request.ApplyVoucherRequest;
import com.capstone.orderservice.dto.request.BookingSessionData;
import com.capstone.orderservice.dto.request.CreateVoucherRequest;
import com.capstone.orderservice.dto.request.UpdateVoucherRequest;
import com.capstone.orderservice.dto.response.ApplyVoucherResponse;
import com.capstone.orderservice.dto.response.VoucherResponse;
import com.capstone.orderservice.entity.Order;
import com.capstone.orderservice.entity.Voucher;
import com.capstone.orderservice.enums.VoucherStatus;
import com.capstone.orderservice.exception.AppException;
import com.capstone.orderservice.exception.ErrorCode;
import com.capstone.orderservice.repository.OrderRepository;
import com.capstone.orderservice.repository.VoucherRepository;
import com.capstone.orderservice.util.VoucherUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class VoucherService {
    private final VoucherRepository voucherRepository;
    private final VoucherUtil voucherUtil;
    private final OrderRepository orderRepository;
    private final org.springframework.data.redis.core.RedisTemplate<String, Object> redisTemplate;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Transactional
    public VoucherResponse createVoucher(CreateVoucherRequest request) {
        if (voucherRepository.existsByVoucherCode(request.getVoucherCode())) {
            throw new AppException(ErrorCode.CONFLICT, "Voucher ID đã tồn tại");
        }

        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Ngày kết thúc phải sau ngày bắt đầu");
        }

        Voucher voucher = Voucher.builder()
                .voucherCode(request.getVoucherCode())
                .minOrderAmount(request.getMinOrderAmount())
                .discountValue(request.getDiscountValue())
                .maxDiscount(request.getMaxDiscount())
                .quantityTotal(request.getQuantityTotal())
                .quantityUsed(0)
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .voucherStatus(VoucherStatus.ACTIVE)
                .build();

        voucher = voucherRepository.save(voucher);

        return VoucherResponse.fromEntity(voucher);
    }

    @Transactional
    public VoucherResponse updateVoucher(Long id, UpdateVoucherRequest request) {
        Voucher voucher = voucherUtil.getVoucherById(id);

        if (request.getMinOrderAmount() != null) {
            voucher.setMinOrderAmount(request.getMinOrderAmount());
        }
        if (request.getDiscountValue() != null) {
            voucher.setDiscountValue(request.getDiscountValue());
        }
        if (request.getMaxDiscount() != null) {
            voucher.setMaxDiscount(request.getMaxDiscount());
        }
        if (request.getQuantityTotal() != null) {
            voucher.setQuantityTotal(request.getQuantityTotal());
        }
        if (request.getStartDate() != null) {
            voucher.setStartDate(request.getStartDate());
        }
        if (request.getEndDate() != null) {
            voucher.setEndDate(request.getEndDate());
        }
        if (request.getVoucherStatus() != null) {
            voucher.setVoucherStatus(request.getVoucherStatus());
        }

        return VoucherResponse.fromEntity(voucher);
    }

    @Transactional(readOnly = true)
    public VoucherResponse getVoucherByVoucherId(Long id) {
        Voucher voucher = voucherUtil.getVoucherById(id);
        return VoucherResponse.fromEntity(voucher);
    }

    @Transactional(readOnly = true)
    public Page<VoucherResponse> getAllVouchers(Pageable pageable) {
        return voucherRepository.findAll(pageable)
                .map(VoucherResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public Page<VoucherResponse> getActiveVouchers(Pageable pageable) {
        LocalDateTime now = LocalDateTime.now();
        return voucherRepository.findActiveVouchers(now, pageable)
                .map(VoucherResponse::fromEntity);
    }

    @Transactional
    public boolean deleteVoucher(Long id) {
        Voucher voucher = voucherUtil.getVoucherById(id);

        voucherRepository.delete(voucher);
        return true;
    }

    @Transactional(readOnly = true)
    public ApplyVoucherResponse applyVoucher(ApplyVoucherRequest request) {
        String sessionDataKey = "booking:data:" + request.getSessionId();
        String sessionJson = (String) redisTemplate.opsForValue().get(sessionDataKey);
        if (sessionJson == null) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Booking session đã hết hạn hoặc không tồn tại");
        }

        BookingSessionData sessionData;
        try {
            sessionData = objectMapper.readValue(sessionJson, BookingSessionData.class);
        } catch (JsonProcessingException e) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "Lỗi đọc dữ liệu booking session");
        }

        BigDecimal totalAmount = sessionData.getTotalAmount();
        if (totalAmount == null) {
            totalAmount = sessionData.getItems().stream()
                    .filter(item -> item.getPrice() != null && item.getQty() != null)
                    .map(item -> item.getPrice().multiply(BigDecimal.valueOf(item.getQty())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        Voucher voucher = voucherRepository.findByVoucherCode(request.getVoucherCode())
                .orElseThrow(() -> new AppException(ErrorCode.BAD_REQUEST, "Voucher không tồn tại"));

        voucher.validateVoucher();

        if (totalAmount.compareTo(voucher.getMinOrderAmount()) < 0) {
            throw new AppException(ErrorCode.BAD_REQUEST,
                    String.format("Đơn hàng phải có giá trị tối thiểu %s để áp dụng voucher này", voucher.getMinOrderAmount()));
        }

        BigDecimal discountAmount = voucher.getDiscountValue();
        if (voucher.getMaxDiscount() != null && discountAmount.compareTo(voucher.getMaxDiscount()) > 0) {
            discountAmount = voucher.getMaxDiscount();
        }

        discountAmount = discountAmount.min(totalAmount);
        BigDecimal finalAmount = totalAmount.subtract(discountAmount).setScale(2, RoundingMode.HALF_UP);

        return ApplyVoucherResponse.builder()
                .totalAmount(totalAmount)
                .discountAmount(discountAmount)
                .finalAmount(finalAmount)
                .voucherCode(voucher.getVoucherCode())
                .build();
    }

    @Transactional
    public void applyVoucher(Order order, String voucherCode) {
        if (voucherCode == null || voucherCode.isBlank()) {
            return;
        }

        Voucher voucher = voucherRepository.findByVoucherCode(voucherCode)
                .orElseThrow(() -> new AppException(ErrorCode.BAD_REQUEST, "Voucher không tồn tại"));

        voucher.validateVoucher();

        if (order.getTotalAmount().compareTo(voucher.getMinOrderAmount()) < 0) {
            throw new AppException(
                    ErrorCode.BAD_REQUEST,
                    String.format("Đơn hàng phải có giá trị tối thiểu %s để áp dụng voucher này", voucher.getMinOrderAmount())
            );
        }

        BigDecimal discount = voucher.getDiscountValue();

        if (voucher.getMaxDiscount() != null &&
                discount.compareTo(voucher.getMaxDiscount()) > 0) {
            discount = voucher.getMaxDiscount();
        }

        BigDecimal totalDiscount = discount.min(order.getTotalAmount());

        order.addVoucher(voucher);
        order.setDiscountAmount(totalDiscount);

        order.setFinalAmount(
                order.getTotalAmount()
                        .subtract(totalDiscount)
                        .setScale(2, RoundingMode.HALF_UP)
        );

        orderRepository.save(order);
    }



    @Transactional
    public void incrementVoucherUsage(Long id) {
        Voucher voucher = voucherUtil.getVoucherById(id);
        voucher.setQuantityUsed(voucher.getQuantityUsed() + 1);
        voucherRepository.save(voucher);
    }
}