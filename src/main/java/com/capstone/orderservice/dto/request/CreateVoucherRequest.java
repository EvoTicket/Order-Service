package com.capstone.orderservice.dto.request;

import com.capstone.orderservice.enums.VoucherStatus;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateVoucherRequest {
    @NotBlank(message = "Voucher Code không được để trống")
    private String voucherCode;

    @NotNull(message = "Số tiền đơn hàng tối thiểu không được để trống")
    @DecimalMin(value = "0.0", message = "Số tiền đơn hàng tối thiểu phải lớn hơn 0")
    private BigDecimal minOrderAmount;

    @NotNull(message = "Giá trị giảm giá không được để trống")
    @DecimalMin(value = "0.0", message = "Giá trị giảm giá phải lớn hơn 0")
    private BigDecimal discountValue;

    private BigDecimal maxDiscount;

    @NotNull(message = "Tổng số lượng không được để trống")
    @Min(value = 1, message = "Tổng số lượng phải lớn hơn 0")
    private Integer quantityTotal;

    @NotNull(message = "Ngày bắt đầu không được để trống")
    private LocalDateTime startDate;

    @NotNull(message = "Ngày kết thúc không được để trống")
    private LocalDateTime endDate;
}