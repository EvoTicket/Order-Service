package com.capstone.orderservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrganizerOrdersStatsResponse {
    private Map<Long, Long> resaleVolumeMap;
    private Map<Long, BigDecimal> royaltyFeeMap;
    private Map<String, BigDecimal> dailyRevenueMap;
}
