package com.capstone.orderservice.dto.response;

import com.capstone.orderservice.entity.OrderVoucher;
import com.capstone.orderservice.entity.Voucher;
import com.capstone.orderservice.enums.VoucherStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoucherResponse {
    private Long id;
    private String voucherCode;
    private BigDecimal minOrderAmount;
    private BigDecimal discountValue;
    private BigDecimal maxDiscount;
    private Integer quantityTotal;
    private Integer quantityUsed;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private VoucherStatus voucherStatus;

    public static VoucherResponse fromOrderVoucher(OrderVoucher orderVoucher) {
        return VoucherResponse.builder()
                .id(orderVoucher.getVoucher().getId())
                .voucherCode(orderVoucher.getVoucher().getVoucherCode())
                .minOrderAmount(orderVoucher.getVoucher().getMinOrderAmount())
                .discountValue(orderVoucher.getVoucher().getDiscountValue())
                .maxDiscount(orderVoucher.getVoucher().getMaxDiscount())
                .quantityTotal(orderVoucher.getVoucher().getQuantityTotal())
                .quantityUsed(orderVoucher.getVoucher().getQuantityUsed())
                .startDate(orderVoucher.getVoucher().getStartDate())
                .endDate(orderVoucher.getVoucher().getEndDate())
                .voucherStatus(orderVoucher.getVoucher().getVoucherStatus())
                .build();
    }

    public static VoucherResponse fromEntity(Voucher voucher) {
        return VoucherResponse.builder()
                .id(voucher.getId())
                .voucherCode(voucher.getVoucherCode())
                .minOrderAmount(voucher.getMinOrderAmount())
                .discountValue(voucher.getDiscountValue())
                .maxDiscount(voucher.getMaxDiscount())
                .quantityTotal(voucher.getQuantityTotal())
                .quantityUsed(voucher.getQuantityUsed())
                .startDate(voucher.getStartDate())
                .endDate(voucher.getEndDate())
                .voucherStatus(voucher.getVoucherStatus())
                .build();
    }
}