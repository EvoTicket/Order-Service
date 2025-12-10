package com.capstone.orderservice.repository;

import com.capstone.orderservice.entity.OrderVoucher;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderVoucherRepository extends JpaRepository<OrderVoucher, Long> {
}
