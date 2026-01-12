package com.capstone.orderservice.service;

import com.capstone.orderservice.dto.request.CreateVoucherRequest;
import com.capstone.orderservice.dto.request.UpdateVoucherRequest;
import com.capstone.orderservice.dto.response.VoucherResponse;
import com.capstone.orderservice.entity.Order;
import com.capstone.orderservice.entity.Voucher;
import com.capstone.orderservice.enums.VoucherStatus;
import com.capstone.orderservice.exception.AppException;
import com.capstone.orderservice.exception.ErrorCode;
import com.capstone.orderservice.repository.OrderRepository;
import com.capstone.orderservice.repository.VoucherRepository;
import com.capstone.orderservice.util.OrderUtil;
import com.capstone.orderservice.util.VoucherUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
    private final OrderUtil orderUtil;
    private final OrderRepository orderRepository;

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

    @Transactional
    public void applyVouchers(Order order, List<Long> voucherIds) {

        List<Voucher> vouchers = voucherRepository
                .findAllById(voucherIds);

        if (vouchers.size() != voucherIds.size()) {
            throw new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Một số voucher không tồn tại");
        }

        BigDecimal totalDiscount = BigDecimal.ZERO;

        for (Voucher voucher : vouchers) {

            voucher.validateVoucher();

            if (order.getTotalAmount().compareTo(voucher.getMinOrderAmount()) < 0) {
                throw new AppException(
                        ErrorCode.BAD_REQUEST,
                        "Đơn hàng phải có giá trị tối thiểu %s để áp dụng voucher này"
                );
            }

            BigDecimal discount = voucher.getDiscountValue();

            if (voucher.getMaxDiscount() != null &&
                    discount.compareTo(voucher.getMaxDiscount()) > 0) {
                discount = voucher.getMaxDiscount();
            }

            totalDiscount = totalDiscount.add(discount);

            order.addVoucher(voucher);
        }

        totalDiscount = totalDiscount.min(order.getTotalAmount());

        order.setDiscountAmount(order.getDiscountAmount().add(totalDiscount));

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