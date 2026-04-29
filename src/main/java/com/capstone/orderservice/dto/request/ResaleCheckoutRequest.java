package com.capstone.orderservice.dto.request;

import com.capstone.orderservice.enums.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResaleCheckoutRequest {
    @NotNull(message = "Payment method is required")
    private PaymentMethod paymentMethod;

    private String fullName;

    private String email;

    private String phoneNumber;
}
