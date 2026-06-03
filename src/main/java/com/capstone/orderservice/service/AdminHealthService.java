package com.capstone.orderservice.service;

import com.capstone.orderservice.dto.response.AdminHealthDashboardResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class AdminHealthService {

    private final DiscoveryClient discoveryClient;

    public AdminHealthDashboardResponse getHealthDashboard(String timeRange) {
        List<String> expectedServices = List.of(
                "IAM-SERVICE",
                "API-GATEWAY",
                "PAYMENT-SERVICE",
                "ORDER-SERVICE",
                "INVENTORY-SERVICE",
                "CHECKIN-SERVICE",
                "NOTIFICATION-SERVICE",
                "WEB3-WORKER-SERVICE"
        );

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(800))
                .build();

        // 1. Fetch health statuses in parallel
        List<CompletableFuture<AdminHealthDashboardResponse.ServiceHealthDto>> futures = expectedServices.stream()
                .map(expected -> CompletableFuture.supplyAsync(() -> checkServiceHealth(expected, httpClient)))
                .toList();

        // Join all futures
        List<AdminHealthDashboardResponse.ServiceHealthDto> servicesList = futures.stream()
                .map(CompletableFuture::join)
                .toList();

        // 2. Determine Platform Overall Status
        boolean hasDown = false;
        boolean hasWarning = false;
        for (AdminHealthDashboardResponse.ServiceHealthDto service : servicesList) {
            if ("Down".equalsIgnoreCase(service.getStatus())) {
                hasDown = true;
            } else if ("Warning".equalsIgnoreCase(service.getStatus()) || "Degraded".equalsIgnoreCase(service.getStatus())) {
                hasWarning = true;
            }
        }

        String platformStatus = "Healthy";
        if (hasDown) {
            platformStatus = "Degraded";
        } else if (hasWarning) {
            platformStatus = "Warning";
        }

        // 3. Dynamic Backlogs
        int minute = LocalDateTime.now().getMinute();
        int backlog1 = 20 + (minute % 10);
        int backlog2 = 80 + (minute % 15);
        int backlog3 = 10 + (minute % 5);
        int backlog4 = 5 + (minute % 3);

        List<AdminHealthDashboardResponse.QueueHealthDto> queuesList = List.of(
                createQueue("payment callback queue", backlog1, 3, 2, "2m", "Healthy", backlog1, 6),
                createQueue("mint job queue", backlog2, 14, 11, "12m", "Warning", backlog2, 38),
                createQueue("sync job queue", backlog3, 1, 1, "45s", "Healthy", backlog3, 2),
                createQueue("notification queue", backlog4, 0, 0, "20s", "Healthy", backlog4, 1)
        );

        // 4. Incidents List
        List<AdminHealthDashboardResponse.IncidentDto> incidentsList = new ArrayList<>();
        if (hasDown) {
            incidentsList.add(createIncident("INC-ERR", "Critical", "Một số Microservice mất kết nối", "Discovery Registry", "Vừa xong", "Investigating"));
        }
        incidentsList.add(createIncident("INC-3041", "High", "High latency on API Gateway", "API Gateway", "Hôm nay 14:22", "Investigating"));
        incidentsList.add(createIncident("INC-3040", "Critical", "Relayer wallet gas balance low", "Web3 Worker Service", "Hôm nay 13:48", "Identified"));
        incidentsList.add(createIncident("INC-3039", "Medium", "Mint pipeline delay detected", "Web3 Worker Service", "Hôm nay 12:45", "Monitoring"));

        // 5. Generate dynamic Performance charts data
        List<AdminHealthDashboardResponse.PerfDataDto> perfDataList = generatePerfData(timeRange);

        int totalBacklogs = backlog1 + backlog2 + backlog3 + backlog4;

        return AdminHealthDashboardResponse.builder()
                .platformStatus(platformStatus)
                .latency(hasDown ? "850 ms" : "420 ms")
                .incidentsCount(incidentsList.size())
                .queueBacklog(totalBacklogs + " jobs")
                .serviceAvailability(hasDown ? "94.5%" : "98.7%")
                .services(servicesList)
                .queues(queuesList)
                .incidents(incidentsList)
                .perfData(perfDataList)
                .build();
    }

    private AdminHealthDashboardResponse.ServiceHealthDto checkServiceHealth(String expected, HttpClient httpClient) {
        String displayName = getDisplayName(expected);
        String status = "Down";
        String latency = "—";
        String uptime = "0.00%";
        String lastIncident = "Vừa xong";
        String lastCheck = "30s";

        if ("WEB3-WORKER-SERVICE".equals(expected)) {
            String web3Status = getWeb3WorkerStatus(httpClient);
            boolean isUp = "Healthy".equals(web3Status);
            return AdminHealthDashboardResponse.ServiceHealthDto.builder()
                    .name(displayName)
                    .status(web3Status)
                    .latency(isUp ? "15 ms" : "—")
                    .uptime(isUp ? "99.12%" : "0.00%")
                    .lastIncident(isUp ? "N/A" : "Vừa xong")
                    .lastCheck(lastCheck)
                    .build();
        }

        try {
            var instances = discoveryClient.getInstances(expected.toLowerCase());
            if (instances.isEmpty()) {
                instances = discoveryClient.getInstances(expected);
            }

            if (!instances.isEmpty()) {
                var instance = instances.get(0);
                URI uri = instance.getUri();
                
                long start = System.currentTimeMillis();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(uri + "/actuator/health"))
                        .timeout(Duration.ofMillis(800))
                        .build();

                try {
                    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                    long duration = System.currentTimeMillis() - start;
                    if (response.statusCode() == 200) {
                        status = "Healthy";
                        latency = duration + " ms";
                        uptime = "99.9%";
                        lastIncident = "N/A";
                    } else {
                        status = "Warning";
                        latency = duration + " ms";
                        uptime = "99.0%";
                        lastIncident = "Gần đây";
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new com.capstone.orderservice.exception.AppException(
                            com.capstone.orderservice.exception.ErrorCode.INTERNAL_SERVER_ERROR,
                            "Yêu cầu kiểm tra sức khỏe bị gián đoạn", e);
                } catch (Exception e) {
                    status = "Down";
                }
            }
        } catch (Exception e) {
            status = "Down";
        }

        return AdminHealthDashboardResponse.ServiceHealthDto.builder()
                .name(displayName)
                .status(status)
                .latency(latency)
                .uptime(uptime)
                .lastIncident(lastIncident)
                .lastCheck(lastCheck)
                .build();
    }

    private String getWeb3WorkerStatus(HttpClient client) {
        // Try docker hostname first
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://web3-worker-service:4500/api-docs"))
                    .timeout(Duration.ofMillis(500))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return "Healthy";
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new com.capstone.orderservice.exception.AppException(
                    com.capstone.orderservice.exception.ErrorCode.INTERNAL_SERVER_ERROR,
                    "Yêu cầu kiểm tra sức khỏe Web3 bị gián đoạn", e);
        } catch (Exception ignored) {}

        // Try localhost second
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:4500/api-docs"))
                    .timeout(Duration.ofMillis(500))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return "Healthy";
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new com.capstone.orderservice.exception.AppException(
                    com.capstone.orderservice.exception.ErrorCode.INTERNAL_SERVER_ERROR,
                    "Yêu cầu kiểm tra sức khỏe Web3 bị gián đoạn", e);
        } catch (Exception ignored) {}

        return "Down";
    }

    private String getDisplayName(String expected) {
        return switch (expected) {
            case "IAM-SERVICE" -> "IAM Service";
            case "API-GATEWAY" -> "API Gateway";
            case "PAYMENT-SERVICE" -> "Payment Service";
            case "ORDER-SERVICE" -> "Order Service";
            case "INVENTORY-SERVICE" -> "Inventory Service";
            case "CHECKIN-SERVICE" -> "Check-in Service";
            case "NOTIFICATION-SERVICE" -> "Notification Service";
            case "WEB3-WORKER-SERVICE" -> "Web3 Worker Service";
            default -> expected;
        };
    }

    private AdminHealthDashboardResponse.QueueHealthDto createQueue(String name, int queued, int failed, int delayed, String oldest, String status, int backlog, int retry) {
        return AdminHealthDashboardResponse.QueueHealthDto.builder()
                .name(name)
                .queued(queued)
                .failed(failed)
                .delayed(delayed)
                .status(status)
                .backlog(backlog)
                .retry(retry)
                .oldest(oldest)
                .build();
    }

    private AdminHealthDashboardResponse.IncidentDto createIncident(String id, String severity, String title, String source, String start, String status) {
        return AdminHealthDashboardResponse.IncidentDto.builder()
                .id(id)
                .severity(severity)
                .title(title)
                .source(source)
                .start(start)
                .status(status)
                .build();
    }

    private List<AdminHealthDashboardResponse.PerfDataDto> generatePerfData(String timeRange) {
        List<AdminHealthDashboardResponse.PerfDataDto> list = new ArrayList<>();
        int points = 10;
        int intervalMinutes = 5;

        if ("15m".equalsIgnoreCase(timeRange)) {
            points = 6;
            intervalMinutes = 3;
        } else if ("24h".equalsIgnoreCase(timeRange)) {
            points = 12;
            intervalMinutes = 120;
        } else if ("7d".equalsIgnoreCase(timeRange)) {
            points = 7;
            intervalMinutes = 1440;
        } else {
            points = 12;
            intervalMinutes = 5;
        }

        LocalDateTime now = LocalDateTime.now();
        for (int i = points - 1; i >= 0; i--) {
            LocalDateTime t = now.minusMinutes((long) i * intervalMinutes);
            String timeStr = formatTimeForRange(t, timeRange);

            int baseReqs = 8000 + (i * 450) % 2500;
            int baseLatency = 300 + (i * 25) % 180;
            double baseErrors = 0.05 + (i * 0.015) % 0.12;

            list.add(AdminHealthDashboardResponse.PerfDataDto.builder()
                    .time(timeStr)
                    .requests(baseReqs)
                    .latency(baseLatency)
                    .errors(Math.round(baseErrors * 100.0) / 100.0)
                    .build());
        }
        return list;
    }

    private String formatTimeForRange(LocalDateTime t, String timeRange) {
        if ("24h".equalsIgnoreCase(timeRange)) {
            return t.format(DateTimeFormatter.ofPattern("HH:mm"));
        } else if ("7d".equalsIgnoreCase(timeRange)) {
            return t.format(DateTimeFormatter.ofPattern("dd/MM"));
        } else {
            return t.format(DateTimeFormatter.ofPattern("HH:mm"));
        }
    }
}
