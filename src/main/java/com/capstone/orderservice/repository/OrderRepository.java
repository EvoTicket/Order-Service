package com.capstone.orderservice.repository;

import com.capstone.orderservice.entity.Order;
import com.capstone.orderservice.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByOrderCode(String orderCode);

    Page<Order> findByUserId(Long userId, Pageable pageable);

    Page<Order> findByOrderStatus(OrderStatus status, Pageable pageable);

    Page<Order> findByUserIdAndOrderStatus(Long userId, OrderStatus status, Pageable pageable);

    List<Order> findByOrderStatusAndCreatedAtBefore(OrderStatus status, LocalDateTime dateTime);

    @Query("SELECT DISTINCT o.eventId FROM Order o WHERE o.userId = :userId AND o.orderStatus = 'CONFIRMED'")
    List<Long> findPurchasedEventIdsByUserId(@Param("userId") Long userId);

    @Query("SELECT o.eventId, COUNT(o.id) FROM Order o WHERE o.userId = :userId AND o.orderStatus = 'CONFIRMED' GROUP BY o.eventId")
    List<Object[]> countPurchasesByEventForUser(@Param("userId") Long userId);

    @Query("SELECT o.eventId, " +
            "SUM(CASE WHEN o.createdAt >= :oneDayAgo THEN o.finalAmount ELSE 0 END), " +
            "SUM(CASE WHEN o.createdAt >= :twoDaysAgo AND o.createdAt < :oneDayAgo THEN o.finalAmount ELSE 0 END) " +
            "FROM Order o " +
            "WHERE o.eventId IN :eventIds AND o.orderStatus = 'CONFIRMED' AND o.createdAt >= :twoDaysAgo " +
            "GROUP BY o.eventId")
    List<Object[]> calculateVolumeForEvents(@Param("eventIds") List<Long> eventIds,
                                            @Param("oneDayAgo") LocalDateTime oneDayAgo,
                                            @Param("twoDaysAgo") LocalDateTime twoDaysAgo);

}
