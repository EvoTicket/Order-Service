package com.capstone.orderservice.repository;

import com.capstone.orderservice.entity.ResaleListing;
import com.capstone.orderservice.enums.ResaleListingStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface ResaleListingRepository extends JpaRepository<ResaleListing, Long>, JpaSpecificationExecutor<ResaleListing> {
    Optional<ResaleListing> findByListingCode(String listingCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM ResaleListing r WHERE r.listingCode = :listingCode")
    Optional<ResaleListing> findByListingCodeForUpdate(@Param("listingCode") String listingCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM ResaleListing r WHERE r.paymentOrder.id = :paymentOrderId")
    Optional<ResaleListing> findByPaymentOrder_IdForUpdate(@Param("paymentOrderId") Long paymentOrderId);

    Optional<ResaleListing> findByPaymentOrder_Id(Long paymentOrderId);

    boolean existsByTicketAsset_IdAndStatusIn(Long ticketAssetId, Collection<ResaleListingStatus> statuses);

    long countByStatus(ResaleListingStatus status);

    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE ResaleListing r SET r.viewCount = r.viewCount + 1 WHERE r.id = :id")
    void incrementViewCount(@Param("id") Long id);

    List<ResaleListing> findAllByStatusAndReservedUntilBefore(ResaleListingStatus status, LocalDateTime now);

    List<ResaleListing> findAllByStatusAndSoldAtAfter(ResaleListingStatus status, LocalDateTime since);

    Optional<ResaleListing> findByReservationSessionId(String sessionId);

    @Query("""
        SELECT r.ticketAsset.eventId, COUNT(r.id), SUM(r.organizerRoyaltyAmount)
        FROM ResaleListing r
        WHERE r.ticketAsset.eventId IN :eventIds AND r.status = 'SOLD' AND r.soldAt >= :since
        GROUP BY r.ticketAsset.eventId
    """)
    List<Object[]> getResaleStatsForEvents(@Param("eventIds") List<Long> eventIds, @Param("since") LocalDateTime since);

    @Query("SELECT COUNT(r) FROM ResaleListing r WHERE r.listingPrice > r.priceCap")
    long countOverCap();

    @Query("SELECT COUNT(r) FROM ResaleListing r WHERE r.listingPrice <= r.priceCap")
    long countWithinCap();

    @Query("SELECT COUNT(r) FROM ResaleListing r WHERE r.listingPrice > r.priceCap * 0.95 AND r.listingPrice <= r.priceCap")
    long countNearCap();

    @Query("SELECT SUM(r.organizerRoyaltyAmount) FROM ResaleListing r WHERE r.status = 'SOLD'")
    BigDecimal sumOrganizerRoyalty();

    @Query("SELECT AVG(r.listingPrice) FROM ResaleListing r")
    BigDecimal averageListingPrice();

    @Query("""
        SELECT r FROM ResaleListing r
        WHERE (:search IS NULL OR
               LOWER(r.listingCode) LIKE :search OR
               LOWER(r.ticketAsset.eventName) LIKE :search OR
               CAST(r.sellerId AS string) LIKE :search)
          AND (:hasStatuses = false OR r.status IN :statuses)
          AND (:overCap IS NULL OR (r.listingPrice > r.priceCap) = :overCap)
    """)
    Page<ResaleListing> searchListings(
            @Param("search") String search,
            @Param("statuses") Collection<ResaleListingStatus> statuses,
            @Param("hasStatuses") boolean hasStatuses,
            @Param("overCap") Boolean overCap,
            Pageable pageable
    );
}
