package com.capstone.orderservice.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderConfirmEvent {
    private String email;
    private String fullName;

    private String orderCode;
    private BigDecimal totalAmount;
    private String discountCode;
    private BigDecimal discountAmount;
    private String ticketDownloadUrl;

    // Event
    private String eventName;
    private String eventDate;          // VD: "Thứ Bảy, 20/07/2024"
    private String eventTime;          // VD: "18:00 - 22:00"
    private String eventLocation;
    private String eventAddress;
    private String organizerName;

    // Showtime (nếu event có nhiều suất diễn)
    private String showtimeDate;       // VD: "Thứ Bảy, 20/07/2024"
    private String showtimeTime;       // VD: "18:00 - 22:00"
    private String showtimeLocation;
    private String showtimeAddress;

    // Payment
    private String paymentMethod;      // VD: "VNPay", "Momo"
    private String transactionId;
    private String paidAt;             // VD: "20/07/2024 lúc 15:32:10"

    // Tickets
    private List<TicketItemDto> ticketItems;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TicketItemDto {
        private String ticketTypeName;
        private Long quantity;
        private BigDecimal unitPrice;
        private BigDecimal subtotal;
    }
}