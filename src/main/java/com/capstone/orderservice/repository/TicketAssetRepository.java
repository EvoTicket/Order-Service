package com.capstone.orderservice.repository;

import com.capstone.orderservice.entity.TicketAsset;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import com.capstone.orderservice.enums.TicketAccessStatus;
import com.capstone.orderservice.enums.TicketChainStatus;
import java.util.Collection;

@Repository
public interface TicketAssetRepository extends JpaRepository<TicketAsset, Long> {
    List<TicketAsset> findByAccessStatusAndTokenIdIsNotNullAndChainStatusIn(
            TicketAccessStatus accessStatus,
            Collection<TicketChainStatus> chainStatuses
    );
    boolean existsByOrderItem_Id(Long orderItemId);

    Optional<TicketAsset> findByOrderItem_Id(Long orderItemId);

    @Query("SELECT t FROM TicketAsset t " +
           "JOIN FETCH t.orderItem oi " +
           "JOIN FETCH oi.order " +
           "WHERE t.currentOwnerId = :currentOwnerId")
    List<TicketAsset> findByCurrentOwnerId(@Param("currentOwnerId") Long currentOwnerId);

    Optional<TicketAsset> findByIdAndCurrentOwnerId(Long id, Long currentOwnerId);

    Optional<TicketAsset> findByTicketCodeOrAssetCode(String ticketCode, String assetCode);
    
    Optional<TicketAsset> findByTokenId(String tokenId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM TicketAsset t WHERE t.id = :id")
    Optional<TicketAsset> findByIdForUpdate(@Param("id") Long id);

    @Query("""
        SELECT t FROM TicketAsset t
        LEFT JOIN t.orderItem oi
        LEFT JOIN oi.order o
        WHERE (:search IS NULL OR
               LOWER(t.ticketCode) LIKE :search OR
               LOWER(t.assetCode) LIKE :search OR
               LOWER(t.eventName) LIKE :search OR
               LOWER(o.fullName) LIKE :search OR
               LOWER(o.email) LIKE :search)
    """)
    org.springframework.data.domain.Page<TicketAsset> searchTickets(@Param("search") String search, org.springframework.data.domain.Pageable pageable);

    long countByChainStatus(TicketChainStatus chainStatus);

    long countByAccessStatus(TicketAccessStatus accessStatus);
}
