package com.capstone.orderservice.repository;

import com.capstone.orderservice.entity.ResaleListing;
import com.capstone.orderservice.enums.ResaleListingStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.math.BigDecimal;
import java.util.Optional;

@Repository
public interface ResaleListingRepository extends JpaRepository<ResaleListing, Long> {
    Optional<ResaleListing> findByListingCode(String listingCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM ResaleListing r WHERE r.listingCode = :listingCode")
    Optional<ResaleListing> findByListingCodeForUpdate(@Param("listingCode") String listingCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM ResaleListing r WHERE r.paymentOrder.id = :paymentOrderId")
    Optional<ResaleListing> findByPaymentOrder_IdForUpdate(@Param("paymentOrderId") Long paymentOrderId);

    boolean existsByTicketAsset_IdAndStatusIn(Long ticketAssetId, Collection<ResaleListingStatus> statuses);

    @Query("""
            SELECT r FROM ResaleListing r
            JOIN r.ticketAsset t
            WHERE r.status = :status
            AND (:eventId IS NULL OR t.eventId = :eventId)
            AND (:ticketTypeId IS NULL OR t.ticketTypeId = :ticketTypeId)
            AND (:minPrice IS NULL OR r.listingPrice >= :minPrice)
            AND (:maxPrice IS NULL OR r.listingPrice <= :maxPrice)
            """)
    Page<ResaleListing> findActiveListings(
            @Param("status") ResaleListingStatus status,
            @Param("eventId") Long eventId,
            @Param("ticketTypeId") Long ticketTypeId,
            @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice,
            Pageable pageable
    );
}
