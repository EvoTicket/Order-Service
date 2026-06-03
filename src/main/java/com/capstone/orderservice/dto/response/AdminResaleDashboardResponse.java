package com.capstone.orderservice.dto.response;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class AdminResaleDashboardResponse {
    private List<StatCardDto> stats;
    private List<VolumeDataDto> volumeData;
    private List<PriceTrendDataDto> priceTrendData;
    private List<TopEventDto> topEvents;
    private List<SpikeDto> spikes;
    private PageResponseDto<ResaleListingDto> listings;
    private List<DisputeDto> disputes;
    private List<ComplianceBarDto> compliance;

    @Data
    @Builder
    public static class StatCardDto {
        private String label;
        private String value;
        private String sub;
        private String color;
    }

    @Data
    @Builder
    public static class VolumeDataDto {
        private String day;
        private int volume;
    }

    @Data
    @Builder
    public static class PriceTrendDataDto {
        private String day;
        private double price;
    }

    @Data
    @Builder
    public static class TopEventDto {
        private String name;
        private int transactions;
        private int flags;
        private double percentage;
    }

    @Data
    @Builder
    public static class SpikeDto {
        private String level;
        private String title;
        private String desc;
        private double percent;
        private int count;
        private String source;
        private int time;
    }

    @Data
    @Builder
    public static class ComplianceBarDto {
        private String label;
        private int count;
        private int total;
        private String color;
    }

    @Data
    @Builder
    public static class ResaleListingDto {
        private String id;
        private String event;
        private String seller;
        private String tier;
        private String price;
        private String listingLimit;
        private String cap; // "Within cap" | "Over cap"
        private String status; // "Active" | "Locked" | "Over cap" | "Under review" | "Removed"
        private String flag;
        private String note;
        private List<HistoryDto> hisListings;
        private String previousOwner;
    }

    @Data
    @Builder
    public static class HistoryDto {
        private String time;
        private String text;
    }

    @Data
    @Builder
    public static class DisputeDto {
        private String id;
        private String ticket;
        private String event;
        private String parties;
        private String type;
        private String priority;
        private String status;
        private String lastUpdate;
        private String description;
        private String note;
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
