package com.capstone.orderservice.repository;

import com.capstone.orderservice.entity.Voucher;
import com.capstone.orderservice.enums.VoucherStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface VoucherRepository extends JpaRepository<Voucher, Long> {
    Optional<Voucher> findByVoucherCode(String voucherCode);

    boolean existsByVoucherCode(String voucherCode);

    @Query("SELECT v FROM Voucher v WHERE v.voucherStatus = 'ACTIVE' " +
            "AND v.startDate <= :now AND v.endDate >= :now " +
            "AND v.quantityUsed < v.quantityTotal")
    Page<Voucher> findActiveVouchers(LocalDateTime now, Pageable pageable);

    Page<Voucher> findByVoucherStatus(VoucherStatus status, Pageable pageable);
}