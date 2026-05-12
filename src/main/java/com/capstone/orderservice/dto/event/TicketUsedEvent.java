package com.capstone.orderservice.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketUsedEvent {
    private Long ticketAssetId;
    private Instant usedAt;
    private Long usedByCheckerId;
    private String usedAtGateId;
}
