package com.capstone.orderservice.service;

import com.capstone.orderservice.dto.response.AdminSupportDashboardResponse;
import com.capstone.orderservice.entity.Order;
import com.capstone.orderservice.entity.TicketAsset;
import com.capstone.orderservice.enums.OrderStatus;
import com.capstone.orderservice.enums.TicketAccessStatus;
import com.capstone.orderservice.enums.TicketChainStatus;
import com.capstone.orderservice.repository.OrderRepository;
import com.capstone.orderservice.repository.TicketAssetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminSupportService {

    private final OrderRepository orderRepository;
    private final TicketAssetRepository ticketAssetRepository;

    public AdminSupportDashboardResponse getSupportDashboard(String tab, String search, int page, int size) {
        int pageIndex = Math.max(0, page - 1);
        Pageable pageable = PageRequest.of(pageIndex, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        String searchPattern = (search != null && !search.trim().isEmpty()) ? "%" + search.toLowerCase().trim() + "%" : null;

        // 1. Query Statistics
        long totalOrders = orderRepository.count();
        long pendingOrders = orderRepository.countByOrderStatus(OrderStatus.PENDING);
        long mintPendingTickets = ticketAssetRepository.countByChainStatus(TicketChainStatus.MINT_PENDING);
        long mintFailedTickets = ticketAssetRepository.countByChainStatus(TicketChainStatus.MINT_FAILED);

        long totalTickets = ticketAssetRepository.count();
        long activeTickets = ticketAssetRepository.countByAccessStatus(TicketAccessStatus.VALID);
        long checkedInTickets = ticketAssetRepository.countByAccessStatus(TicketAccessStatus.CHECKED_IN)
                + ticketAssetRepository.countByAccessStatus(TicketAccessStatus.USED);
        long lockedResaleTickets = ticketAssetRepository.countByAccessStatus(TicketAccessStatus.LOCKED_RESALE);

        List<AdminSupportDashboardResponse.StatItem> transactionStats = List.of(
                createStatItem("Tổng kết quả", String.format("%,d", totalOrders), "gray", "FileText"),
                createStatItem("Thanh toán chờ", String.format("%,d", pendingOrders), "amber", "CreditCard"),
                createStatItem("Mint đang chờ", String.format("%,d", mintPendingTickets), "indigo", "Ticket"),
                createStatItem("Nhật ký lỗi", String.format("%,d", mintFailedTickets), "rose", "AlertCircle")
        );

        List<AdminSupportDashboardResponse.StatItem> ticketStats = List.of(
                createStatItem("Tổng số vé", String.format("%,d", totalTickets), "gray", "Ticket"),
                createStatItem("Vé đang hiệu lực", String.format("%,d", activeTickets), "emerald", "ShieldCheck"),
                createStatItem("Đã check-in", String.format("%,d", checkedInTickets), "indigo", "CheckCircle2"),
                createStatItem("Đang rao bán", String.format("%,d", lockedResaleTickets), "amber", "Zap")
        );

        List<AdminSupportDashboardResponse.StatItem> caseStats = List.of(
                createStatItem("Tổng số case", "318", "gray", "MessageSquare"),
                createStatItem("Đang chờ xử lý", "12", "amber", "Clock"),
                createStatItem("Cần hỗ trợ gấp", "4", "rose", "AlertCircle"),
                createStatItem("Thời gian phản hồi", "14m", "sky", "Zap")
        );

        AdminSupportDashboardResponse.StatsDto statsDto = AdminSupportDashboardResponse.StatsDto.builder()
                .transactions(transactionStats)
                .tickets(ticketStats)
                .cases(caseStats)
                .build();

        // 2. Fetch Lists Based on activeTab
        AdminSupportDashboardResponse.PageResponseDto<AdminSupportDashboardResponse.TransactionDto> transactionsPage = null;
        AdminSupportDashboardResponse.PageResponseDto<AdminSupportDashboardResponse.TicketDto> ticketsPage = null;
        AdminSupportDashboardResponse.PageResponseDto<AdminSupportDashboardResponse.CaseDto> casesPage = null;

        if ("transactions".equalsIgnoreCase(tab)) {
            Page<Order> orders = orderRepository.searchOrders(searchPattern, pageable);
            List<AdminSupportDashboardResponse.TransactionDto> dtos = orders.getContent().stream()
                    .map(this::mapToTransactionDto)
                    .toList();
            transactionsPage = AdminSupportDashboardResponse.PageResponseDto.<AdminSupportDashboardResponse.TransactionDto>builder()
                    .content(dtos)
                    .totalElements(orders.getTotalElements())
                    .totalPages(orders.getTotalPages())
                    .size(orders.getSize())
                    .number(orders.getNumber())
                    .build();
        } else if ("tickets".equalsIgnoreCase(tab)) {
            Page<TicketAsset> assets = ticketAssetRepository.searchTickets(searchPattern, pageable);
            List<AdminSupportDashboardResponse.TicketDto> dtos = assets.getContent().stream()
                    .map(this::mapToTicketDto)
                    .toList();
            ticketsPage = AdminSupportDashboardResponse.PageResponseDto.<AdminSupportDashboardResponse.TicketDto>builder()
                    .content(dtos)
                    .totalElements(assets.getTotalElements())
                    .totalPages(assets.getTotalPages())
                    .size(assets.getSize())
                    .number(assets.getNumber())
                    .build();
        } else {
            // tab == "cases"
            List<AdminSupportDashboardResponse.CaseDto> mockCases = getMockCases(search);
            int start = (int) pageable.getOffset();
            int end = Math.min((start + pageable.getPageSize()), mockCases.size());
            List<AdminSupportDashboardResponse.CaseDto> subList = (start > mockCases.size()) ? Collections.emptyList() : mockCases.subList(start, end);
            Page<AdminSupportDashboardResponse.CaseDto> pageResult = new PageImpl<>(subList, pageable, mockCases.size());

            casesPage = AdminSupportDashboardResponse.PageResponseDto.<AdminSupportDashboardResponse.CaseDto>builder()
                    .content(pageResult.getContent())
                    .totalElements(pageResult.getTotalElements())
                    .totalPages(pageResult.getTotalPages())
                    .size(pageResult.getSize())
                    .number(pageResult.getNumber())
                    .build();
        }

        return AdminSupportDashboardResponse.builder()
                .stats(statsDto)
                .transactions(transactionsPage)
                .tickets(ticketsPage)
                .cases(casesPage)
                .build();
    }

    private AdminSupportDashboardResponse.StatItem createStatItem(String label, String value, String color, String icon) {
        return AdminSupportDashboardResponse.StatItem.builder()
                .label(label)
                .value(value)
                .color(color)
                .icon(icon)
                .build();
    }

    private AdminSupportDashboardResponse.TransactionDto mapToTransactionDto(Order order) {
        String payment = "Pending";
        if (order.getOrderStatus() == OrderStatus.CONFIRMED) {
            payment = "Success";
        } else if (order.getOrderStatus() == OrderStatus.CANCELLED) {
            payment = "Cancelled";
        } else if (order.getOrderStatus() == OrderStatus.PAYMENT_FAILED) {
            payment = "Failed";
        } else if (order.getOrderStatus() == OrderStatus.EXPIRED) {
            payment = "Expired";
        }

        String mintStatus = "Not started";
        if (!order.getOrderItems().isEmpty()) {
            // Find a corresponding ticket asset
            TicketAsset asset = ticketAssetRepository.findByOrderItem_Id(order.getOrderItems().get(0).getId()).orElse(null);
            if (asset != null) {
                if (asset.getChainStatus() == TicketChainStatus.MINTED) {
                    mintStatus = "Minted";
                } else if (asset.getChainStatus() == TicketChainStatus.MINT_PENDING) {
                    mintStatus = "Mint Pending";
                } else if (asset.getChainStatus() == TicketChainStatus.MINT_FAILED) {
                    mintStatus = "Mint Failed";
                }
            }
        }

        // event details description
        String eventName = "Sự kiện #" + order.getEventId();
        if (!order.getOrderItems().isEmpty()) {
            TicketAsset asset = ticketAssetRepository.findByOrderItem_Id(order.getOrderItems().get(0).getId()).orElse(null);
            if (asset != null && asset.getEventName() != null) {
                eventName = asset.getEventName();
            }
        }

        return AdminSupportDashboardResponse.TransactionDto.builder()
                .id(order.getOrderCode())
                .buyer(order.getFullName())
                .email(order.getEmail())
                .event(eventName)
                .amount(formatAmount(order.getFinalAmount()))
                .payment(payment)
                .mint(mintStatus)
                .updatedAt(formatDate(order.getUpdatedAt()))
                .build();
    }

    private AdminSupportDashboardResponse.TicketDto mapToTicketDto(TicketAsset asset) {
        String owner = "N/A";
        String ownerType = "Buyer gốc";
        if (asset.getOrderItem() != null && asset.getOrderItem().getOrder() != null) {
            owner = asset.getOrderItem().getOrder().getFullName();
            if (asset.getOrderItem().getOrder().getOrderType() != null) {
                ownerType = asset.getOrderItem().getOrder().getOrderType().name().equalsIgnoreCase("RESALE") ? "Resale market" : "Buyer gốc";
            }
        }

        String access = "Active";
        if (asset.getAccessStatus() == TicketAccessStatus.VALID) {
            access = "Active";
        } else if (asset.getAccessStatus() == TicketAccessStatus.LOCKED_RESALE) {
            access = "Locked";
        } else if (asset.getAccessStatus() == TicketAccessStatus.CHECKED_IN || asset.getAccessStatus() == TicketAccessStatus.USED) {
            access = "Used";
        } else if (asset.getAccessStatus() == TicketAccessStatus.CANCELLED) {
            access = "Refunded";
        } else if (asset.getAccessStatus() == TicketAccessStatus.WITHDRAWN) {
            access = "Withdrawn";
        }

        String checkin = "Not yet";
        if (asset.getAccessStatus() == TicketAccessStatus.CHECKED_IN || asset.getAccessStatus() == TicketAccessStatus.USED) {
            checkin = "Checked-in";
        } else if (asset.getAccessStatus() == TicketAccessStatus.CANCELLED) {
            checkin = "Denied";
        }

        String activity = formatDate(asset.getUpdatedAt()) + " • Cập nhật";
        if (asset.getCreatedAt() != null) {
            activity = formatDate(asset.getCreatedAt()) + " • Mua mới";
        }

        return AdminSupportDashboardResponse.TicketDto.builder()
                .id(asset.getTicketCode() != null ? asset.getTicketCode() : asset.getAssetCode())
                .owner(owner)
                .ownerType(ownerType)
                .event(asset.getEventName() != null ? asset.getEventName() : "Sự kiện #" + asset.getEventId())
                .tier(asset.getTicketTypeName() != null ? asset.getTicketTypeName() : "Standard")
                .access(access)
                .checkin(checkin)
                .activity(activity)
                .build();
    }

    private List<AdminSupportDashboardResponse.CaseDto> getMockCases(String search) {
        List<AdminSupportDashboardResponse.CaseDto> cases = new ArrayList<>();
        cases.add(createCase("CASE-50421", "Vé không mint sau thanh toán", "Đặng Thu Thảo", "thao.dt@gmail.com", "Anh Trai Say Hi Concert 2026", "High", "Escalated", "@admin.linh", "25/04 10:02"));
        cases.add(createCase("CASE-50418", "Yêu cầu hoàn tiền do trùng vé", "Nguyễn Hoàng Anh", "anh.nh@gmail.com", "Đêm nhạc Acoustic Sài Gòn", "Medium", "In Progress", "@support.minh", "25/04 09:30"));
        cases.add(createCase("CASE-50410", "Phát hiện resale vượt giới hạn", "Phạm Quốc Đạt", "dat.pq@yahoo.com", "Anh Trai Say Hi Concert 2026", "High", "Open", "Chưa gán", "25/04 08:50"));
        cases.add(createCase("CASE-50402", "Không nhận được email xác nhận", "Lê Thanh Hà", "ha.le@protonmail.com", "Workshop UX cho doanh nghiệp", "Low", "Waiting", "@support.hoa", "24/04 21:15"));
        cases.add(createCase("CASE-50398", "Tài khoản bị khóa sai", "Trần Mỹ Linh", "linh.tran@outlook.com", "—", "Medium", "Resolved", "@admin.duy", "24/04 16:48"));
        cases.add(createCase("CASE-50384", "Yêu cầu đổi tên trên vé", "Bùi Quang Minh", "minh.bq@gmail.com", "Giải chạy Marathon Hà Nội", "Low", "Resolved", "@support.minh", "24/04 11:20"));

        if (search != null && !search.trim().isEmpty()) {
            String lowerSearch = search.toLowerCase().trim();
            return cases.stream()
                    .filter(c -> c.getId().toLowerCase().contains(lowerSearch) ||
                            c.getSubject().toLowerCase().contains(lowerSearch) ||
                            c.getUser().toLowerCase().contains(lowerSearch) ||
                            c.getEmail().toLowerCase().contains(lowerSearch) ||
                            c.getEvent().toLowerCase().contains(lowerSearch))
                    .toList();
        }

        return cases;
    }

    private AdminSupportDashboardResponse.CaseDto createCase(String id, String subject, String user, String email, String event, String priority, String status, String assignee, String updatedAt) {
        return AdminSupportDashboardResponse.CaseDto.builder()
                .id(id)
                .subject(subject)
                .user(user)
                .email(email)
                .event(event)
                .priority(priority)
                .status(status)
                .assignee(assignee)
                .updatedAt(updatedAt)
                .build();
    }

    private String formatAmount(BigDecimal amount) {
        if (amount == null) return "₫0";
        return "₫" + String.format("%,d", amount.longValue());
    }

    private String formatDate(LocalDateTime dt) {
        if (dt == null) return "N/A";
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM HH:mm");
        return dt.format(formatter);
    }
}
