package com.capstone.orderservice.entity;

import com.capstone.orderservice.enums.PayoutStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "seller_payouts",
        indexes = {
                @Index(name = "idx_seller_payout_seller_id", columnList = "seller_id"),
                @Index(name = "idx_seller_payout_listing_id", columnList = "resale_listing_id"),
                @Index(name = "idx_seller_payout_status", columnList = "status"),
                @Index(name = "idx_seller_payout_created_at", columnList = "created_at")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SellerPayout {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resale_listing_id", nullable = false)
    private ResaleListing resaleListing;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PayoutStatus status;

    @Column(name = "bank_code")
    private String bankCode;

    @Column(name = "account_number")
    private String accountNumber;

    @Column(name = "account_name")
    private String accountName;

    @Column(name = "transaction_reference")
    private String transactionReference;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "payout_date")
    private LocalDateTime payoutDate;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) {
            status = PayoutStatus.PENDING;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
