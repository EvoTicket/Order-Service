package com.capstone.orderservice.dto.event;

import com.capstone.orderservice.enums.TicketAccessStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketAccessSyncEvent {
    private Long ticketAssetId;
    private String ticketCode;
    private Long eventId;
    private Long showtimeId;
    private Long currentOwnerId;
    private TicketAccessStatus accessStatus;
    private Integer qrVersion;
    private String ticketTypeName;
    private String zoneLabel;
    private String seatLabel;
    private String gatePolicySnapshot;
}
