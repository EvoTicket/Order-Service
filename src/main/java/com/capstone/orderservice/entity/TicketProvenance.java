package com.capstone.orderservice.entity;

import com.capstone.orderservice.enums.ProvenanceActionType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "ticket_provenance",
        indexes = {
                @Index(name = "idx_ticket_provenance_ticket_asset_id", columnList = "ticket_asset_id"),
                @Index(name = "idx_ticket_provenance_action_type", columnList = "action_type"),
                @Index(name = "idx_ticket_provenance_created_at", columnList = "created_at")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketProvenance {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_asset_id", nullable = false)
    private Long ticketAssetId;

    @Column(name = "from_user_id")
    private Long fromUserId;

    @Column(name = "to_user_id")
    private Long toUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false)
    private ProvenanceActionType actionType;

    @Column(name = "order_code")
    private String orderCode;

    @Column(name = "resale_listing_code")
    private String resaleListingCode;

    @Column(name = "price")
    private BigDecimal price;

    @Column(name = "tx_hash")
    private String txHash;

    @Column(name = "token_id")
    private String tokenId;

    @Column(name = "chain_status")
    private String chainStatus;

    @Column(name = "description")
    private String description;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
