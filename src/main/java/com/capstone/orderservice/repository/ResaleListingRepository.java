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

    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE ResaleListing r SET r.viewCount = r.viewCount + 1 WHERE r.id = :id")
    void incrementViewCount(@Param("id") Long id);

    List<ResaleListing> findAllByStatusAndReservedUntilBefore(ResaleListingStatus status, LocalDateTime now);

    Optional<ResaleListing> findByReservationSessionId(String sessionId);
}
