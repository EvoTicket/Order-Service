package com.capstone.orderservice.dto.response;

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
public class ResaleSessionResponse {
    private Long userId;
    private String eventName;
    private LocalDateTime time;
    private String venue;
    private BigDecimal amount;
}
