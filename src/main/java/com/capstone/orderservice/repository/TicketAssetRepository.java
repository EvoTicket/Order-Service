package com.capstone.orderservice.repository;

import com.capstone.orderservice.entity.TicketAsset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TicketAssetRepository extends JpaRepository<TicketAsset, Long> {
    boolean existsByOrderItem_Id(Long orderItemId);

    Optional<TicketAsset> findByOrderItem_Id(Long orderItemId);

    List<TicketAsset> findByCurrentOwnerId(Long currentOwnerId);

    Optional<TicketAsset> findByIdAndCurrentOwnerId(Long id, Long currentOwnerId);
}
