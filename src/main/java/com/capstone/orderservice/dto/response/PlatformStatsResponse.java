package com.capstone.orderservice.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class PlatformStatsResponse {
    private BigDecimal totalGmv;
    private BigDecimal totalRevenue;
    private long totalTicketsSold;
    private List<DailyStatsDto> trend;
}
