package com.capstone.orderservice.entity;

import com.capstone.orderservice.enums.VoucherStatus;
import com.capstone.orderservice.exception.AppException;
import com.capstone.orderservice.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "vouchers")
@Getter @Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Voucher {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "voucher_code")
    private String voucherCode;

    @Column(name = "min_order_amount")
    private BigDecimal minOrderAmount;

    @Column(name = "discount_value")
    private BigDecimal discountValue;

    @Column(name = "max_discount_amount")
    private BigDecimal maxDiscount;

    @Column(name = "quantity_total")
    private Integer quantityTotal;

    @Column(name = "quantity_used")
    private Integer quantityUsed;

    @Column(name = "start_date")
    private LocalDateTime startDate;

    @Column(name = "end_date")
    private LocalDateTime endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "voucher_status")
    private VoucherStatus voucherStatus;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (quantityUsed == null) {
            quantityUsed = 0;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void validateVoucher() {
        if (this.getVoucherStatus() != VoucherStatus.ACTIVE) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "Voucher không khả dụng");
        }

        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(this.getStartDate())) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "Voucher chưa có hiệu lực");
        }
        if (now.isAfter(this.getEndDate())) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "Voucher đã hết hạn");
        }

        if (this.getQuantityUsed() >= this.getQuantityTotal()) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR, "Voucher đã hết lượt sử dụng");
        }
    }
}
