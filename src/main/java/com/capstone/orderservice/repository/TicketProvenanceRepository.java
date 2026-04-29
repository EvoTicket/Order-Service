package com.capstone.orderservice.repository;

import com.capstone.orderservice.entity.TicketProvenance;
import com.capstone.orderservice.enums.ProvenanceActionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TicketProvenanceRepository extends JpaRepository<TicketProvenance, Long> {
    boolean existsByTicketAssetIdAndActionTypeAndOrderCode(
            Long ticketAssetId,
            ProvenanceActionType actionType,
            String orderCode
    );

    boolean existsByTicketAssetIdAndActionTypeAndResaleListingCode(
            Long ticketAssetId,
            ProvenanceActionType actionType,
            String resaleListingCode
    );

    boolean existsByTicketAssetIdAndActionTypeAndOrderCodeAndResaleListingCode(
            Long ticketAssetId,
            ProvenanceActionType actionType,
            String orderCode,
            String resaleListingCode
    );

    List<TicketProvenance> findByTicketAssetIdOrderByCreatedAtAsc(Long ticketAssetId);
}
