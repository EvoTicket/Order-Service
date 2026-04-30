package com.capstone.orderservice.entity;

import com.capstone.orderservice.enums.ResaleListingStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "resale_listings",
        indexes = {
                @Index(name = "idx_resale_listing_ticket_asset_id", columnList = "ticket_asset_id"),
                @Index(name = "idx_resale_listing_seller_id", columnList = "seller_id"),
                @Index(name = "idx_resale_listing_buyer_id", columnList = "buyer_id"),
                @Index(name = "idx_resale_listing_status", columnList = "status"),
                @Index(name = "idx_resale_listing_created_at", columnList = "created_at")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResaleListing {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "listing_code", nullable = false, unique = true)
    private String listingCode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_asset_id", nullable = false)
    private TicketAsset ticketAsset;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(name = "buyer_id")
    private Long buyerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_order_id")
    private Order paymentOrder;

    @Column(name = "original_price", nullable = false)
    private BigDecimal originalPrice;

    @Column(name = "listing_price", nullable = false)
    private BigDecimal listingPrice;

    @Column(name = "price_cap", nullable = false)
    private BigDecimal priceCap;

    @Column(name = "platform_fee_amount", nullable = false)
    private BigDecimal platformFeeAmount;

    @Builder.Default
    @Column(name = "organizer_royalty_amount", nullable = false)
    private BigDecimal organizerRoyaltyAmount = BigDecimal.ZERO;

    @Column(name = "seller_payout_amount", nullable = false)
    private BigDecimal sellerPayoutAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ResaleListingStatus status;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "sold_at")
    private LocalDateTime soldAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (organizerRoyaltyAmount == null) {
            organizerRoyaltyAmount = BigDecimal.ZERO;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
