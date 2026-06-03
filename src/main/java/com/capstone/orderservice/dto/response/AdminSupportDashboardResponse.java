package com.capstone.orderservice.dto.response;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class AdminSupportDashboardResponse {
    private StatsDto stats;
    private PageResponseDto<TransactionDto> transactions;
    private PageResponseDto<TicketDto> tickets;
    private PageResponseDto<CaseDto> cases;

    @Data
    @Builder
    public static class StatsDto {
        private List<StatItem> transactions;
        private List<StatItem> tickets;
        private List<StatItem> cases;
    }

    @Data
    @Builder
    public static class StatItem {
        private String label;
        private String value;
        private String color;
        private String icon;
    }

    @Data
    @Builder
    public static class TransactionDto {
        private String id;
        private String buyer;
        private String email;
        private String event;
        private String amount;
        private String payment; // "Success" | "Pending" | "Failed" | "Expired" | "Cancelled"
        private String mint; // "Minted" | "Mint Pending" | "Mint Failed" | "Not started"
        private String updatedAt; // dd/MM HH:mm
    }

    @Data
    @Builder
    public static class TicketDto {
        private String id;
        private String owner;
        private String ownerType;
        private String event;
        private String tier;
        private String access; // "Active" | "Used" | "Resold" | "Locked" | "Refunded" | "Withdrawn"
        private String checkin; // "Checked-in" | "Denied" | "Not yet" | "—"
        private String activity; // dd/MM HH:mm • Action
    }

    @Data
    @Builder
    public static class CaseDto {
        private String id;
        private String subject;
        private String user;
        private String email;
        private String event;
        private String priority; // "High" | "Medium" | "Low"
        private String status; // "Escalated" | "In Progress" | "Open" | "Resolved" | "Waiting"
        private String assignee;
        private String updatedAt; // dd/MM HH:mm
    }

    @Data
    @Builder
    public static class PageResponseDto<T> {
        private List<T> content;
        private long totalElements;
        private int totalPages;
        private int size;
        private int number;
    }
}
