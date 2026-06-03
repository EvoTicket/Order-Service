package com.capstone.orderservice.dto.response;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class AdminHealthDashboardResponse {
    private String platformStatus; // "Healthy" | "Degraded" | "Down"
    private String latency; // e.g. "420 ms"
    private int incidentsCount;
    private String queueBacklog; // e.g. "128 jobs"
    private String serviceAvailability; // e.g. "98.7%"
    
    private List<ServiceHealthDto> services;
    private List<QueueHealthDto> queues;
    private List<IncidentDto> incidents;
    private List<PerfDataDto> perfData;

    @Data
    @Builder
    public static class ServiceHealthDto {
        private String name;
        private String status; // "Healthy" | "Warning" | "Degraded" | "Down"
        private String latency; // e.g. "118 ms"
        private String uptime; // e.g. "99.99%"
        private String lastIncident; // e.g. "12 ngày trước"
        private String lastCheck; // e.g. "30s"
    }

    @Data
    @Builder
    public static class QueueHealthDto {
        private String name;
        private int queued;
        private int failed;
        private int delayed;
        private String status; // "Healthy" | "Warning" | "Degraded" | "Down"
        private int backlog;
        private int retry;
        private String oldest; // e.g. "2m"
    }

    @Data
    @Builder
    public static class IncidentDto {
        private String id;
        private String severity; // "Critical" | "High" | "Medium" | "Low"
        private String title;
        private String source;
        private String start; // e.g. "Hôm nay 14:22"
        private String status; // "Investigating" | "Identified" | "Monitoring" | "Resolved"
    }

    @Data
    @Builder
    public static class PerfDataDto {
        private String time;
        private int requests;
        private int latency;
        private double errors;
    }
}
