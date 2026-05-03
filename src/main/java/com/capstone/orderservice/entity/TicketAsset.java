package com.capstone.orderservice.entity;

import com.capstone.orderservice.enums.TicketAccessStatus;
import com.capstone.orderservice.enums.TicketChainStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "ticket_assets",
        indexes = {
                @Index(name = "idx_ticket_asset_current_owner_id", columnList = "current_owner_id"),
                @Index(name = "idx_ticket_asset_event_id", columnList = "event_id"),
                @Index(name = "idx_ticket_asset_ticket_type_id", columnList = "ticket_type_id"),
                @Index(name = "idx_ticket_asset_access_status", columnList = "access_status"),
                @Index(name = "idx_ticket_asset_chain_status", columnList = "chain_status")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketAsset {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "asset_code", nullable = false, unique = true)
    private String assetCode;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_item_id", nullable = false, unique = true)
    private OrderItem orderItem;

    @Column(name = "original_order_id", nullable = false)
    private Long originalOrderId;

    @Column(name = "original_order_code", nullable = false)
    private String originalOrderCode;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "event_name")
    private String eventName;

    @Column(name = "showtime_id")
    private Long showtimeId;

    @Column(name = "event_start_time")
    private LocalDateTime eventStartTime;

    @Column(name = "event_end_time")
    private LocalDateTime eventEndTime;

    @Column(name = "venue_name")
    private String venueName;

    @Column(name = "venue_address")
    private String venueAddress;

    @Column(name = "ticket_type_id", nullable = false)
    private Long ticketTypeId;

    @Column(name = "ticket_type_name")
    private String ticketTypeName;

    @Column(name = "ticket_code")
    private String ticketCode;

    @Column(name = "original_price", nullable = false)
    private BigDecimal originalPrice;

    @Column(name = "original_buyer_id", nullable = false)
    private Long originalBuyerId;

    @Column(name = "current_owner_id", nullable = false)
    private Long currentOwnerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "access_status", nullable = false)
    private TicketAccessStatus accessStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "chain_status", nullable = false)
    private TicketChainStatus chainStatus;

    @Column(name = "token_id")
    private String tokenId;

    @Column(name = "tx_hash")
    private String txHash;

    @Column(name = "contract_address")
    private String contractAddress;

    @Column(name = "qr_secret_hash")
    private String qrSecretHash;

    @Builder.Default
    @Column(name = "qr_secret_version", nullable = false)
    private Integer qrSecretVersion = 1;

    @Column(name = "current_resale_listing_id")
    private Long currentResaleListingId;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (qrSecretVersion == null) {
            qrSecretVersion = 1;
        }
        if (accessStatus == null) {
            accessStatus = TicketAccessStatus.VALID;
        }
        if (chainStatus == null) {
            chainStatus = TicketChainStatus.WEB2_ONLY;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
