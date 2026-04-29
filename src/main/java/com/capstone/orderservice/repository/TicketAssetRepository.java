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

@Repository
public interface TicketAssetRepository extends JpaRepository<TicketAsset, Long> {
    boolean existsByOrderItem_Id(Long orderItemId);

    Optional<TicketAsset> findByOrderItem_Id(Long orderItemId);

    List<TicketAsset> findByCurrentOwnerId(Long currentOwnerId);

    Optional<TicketAsset> findByIdAndCurrentOwnerId(Long id, Long currentOwnerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM TicketAsset t WHERE t.id = :id")
    Optional<TicketAsset> findByIdForUpdate(@Param("id") Long id);
}
