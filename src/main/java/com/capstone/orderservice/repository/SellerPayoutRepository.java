package com.capstone.orderservice.repository;

import com.capstone.orderservice.entity.SellerPayout;
import com.capstone.orderservice.enums.PayoutStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SellerPayoutRepository extends JpaRepository<SellerPayout, Long> {
    
    Optional<SellerPayout> findByResaleListingId(Long resaleListingId);
    
    Page<SellerPayout> findBySellerId(Long sellerId, Pageable pageable);
    
    Page<SellerPayout> findByStatus(PayoutStatus status, Pageable pageable);
    
    Page<SellerPayout> findBySellerIdAndStatus(Long sellerId, PayoutStatus status, Pageable pageable);
}
